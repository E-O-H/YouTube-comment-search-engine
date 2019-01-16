package youtubesearcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Indexer for indexing Youtube comments
 * 
 * @author Chenyang Tang
 *
 */
public class YoutubeIndexer {
  
  /*
   * command-line arguments for the entry point
   */
  @Option(name = "-id", aliases = "-i", required = true, 
      usage = "Id of the source scope (e.g. VideoId or ChannelId). Must match the scope option.")
  private String id;
  
  @Option(name = "-path", aliases = "-p", 
          usage = "Path to the directory to save index files (aka. output location).")
  private File indexDir = new File("index");

  @Option(name = "-video", aliases = "-v", forbids = {"-channel"}, 
      usage = "Specify the source scope is a video. Exactly one scope option must be provided.")
  private boolean isVideo = false;
  
  @Option(name = "-channel", aliases = "-c", forbids = {"-video"}, 
      usage = "Specify the source scope is a channel. Exactly one scope option must be provided.")
  private boolean isChannel = false;

  @Option(name = "-api-key", aliases = "-k", 
      usage = "Specify an API key to use. A built-in default one is used if not specified.")
  private String apiKey;
  
  @Option(name = "-help", aliases = "-h", help = true, 
          usage = "Print help text.")
  private boolean printHelp = false;
  
  @SuppressWarnings("deprecation")
  private int parseArgs(String[] args) {
    final CmdLineParser args4jCmdLineParser = new CmdLineParser(this);
    try {
      args4jCmdLineParser.parseArgument(args);
      if (isVideo == false && isChannel == false) 
        throw new CmdLineException("Must provide a source scope specifier option.");
    } catch (final CmdLineException e) {
      System.err.println(e.getMessage());
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 2;
    }
    
    if (printHelp) {
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 1;
    }
    
    return 0;
  }
  
  private static final String URL_BASE = "https://www.googleapis.com/youtube/v3";
  private static String API_KEY = "AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc";
  
  /*
   * Lucene indexer internal objects
   */
  private StandardAnalyzer analyzer; // analyzer for tokenizing text
  private IndexWriterConfig config;  // config for IndexWriter
  private Directory index;           // the index
  
  enum Scope {
    VIDEO, CHANNEL;
  }
  
  /**
   * This class stores all needed information from one downloaded page
   */
  private static class CommentsPage {
    private JsonArray comments;     // the JSON object containing all comments on the page
    private String nextPageToken;   // token for retrieving the next page
    
    public CommentsPage(JsonArray comments, String nextPageToken) {
      this.comments = comments;
      this.nextPageToken = nextPageToken;
    }

    public final JsonArray getComments() {
      return comments;
    }

    public final String getNextPageToken() {
      return nextPageToken;
    }
  }
  
  /**
   * Download one page of the top-level comments within a "scope" 
   * (e.g. a "scope" can be a video or a channel).
   * 
   * @param scope type of the scope
   * @param scopeId ID of scope (e.g. VideoId for VIDEO scope, ChannelId for CHANNEL scope)
   * @param pageToken pageToken of the page (provide null for the first page)
   * @return an object containing information of all comments in the page and a nextPageToken
   */
  private static CommentsPage downloadTopLevelCommentsPage(Scope scope, String scopeId, 
                                                           String pageToken) {
    final String SCOPE_FILTER;
    switch (scope) {
      case VIDEO:
        SCOPE_FILTER = "&videoId=" + scopeId;
        break;
      case CHANNEL:
        SCOPE_FILTER = "&allThreadsRelatedToChannelId=" + scopeId;
        break;
      default:
        System.err.println("Invalid Scope.");
        return null;
    }
    
    final String PAGE_TOKEN = pageToken == null ? "" : "&pageToken=" + pageToken;
    
    String urlStr = URL_BASE + "/commentThreads" + "?key=" + API_KEY 
                    + "&textFormat=plainText&part=snippet" + "&maxResults=100"
                    + SCOPE_FILTER + PAGE_TOKEN;
    
    Connection.Response response;
    try {
       response = Jsoup.connect(urlStr)
                       .method(Connection.Method.GET)
                       .referrer("https://cs.nyu.edu")
                       .ignoreContentType(true)
                       .maxBodySize(Integer.MAX_VALUE)
                       .execute();
    } catch (IOException e) {
      System.err.println(
          "Network error when retrieving the page: " + urlStr + ".");
      System.err.println(e.getMessage());
      return null;
    }
    
    JsonParser parser = new JsonParser();
    JsonObject rootObj = parser.parse(response.body()).getAsJsonObject();
    JsonArray commentsArrayObj = rootObj.getAsJsonArray("items");
    
    String nextPageToken = null;
    try {
      nextPageToken = rootObj.get("nextPageToken").getAsString();
    } catch (NullPointerException e) {
      // Do nothing
    }
    
    return new CommentsPage(commentsArrayObj, nextPageToken);
  }
  
  /**
   * Download one page of reply comments (i.e. replies in a comment thread).
   * 
   * @param parentId The ID of the top-level comment (a.k.a. the thread ID)
   * @param pageToken pageToken of the page (provide null for the first page)
   * @return an object containing information of all comments in the page and a nextPageToken
   */
  private static CommentsPage downloadReplyCommentsPage(String parentId, String pageToken) {
    final String PAGE_TOKEN = pageToken == null ? "" : "&pageToken=" + pageToken;
    
    String urlStr = URL_BASE + "/comments" + "?key=" + API_KEY 
                    + "&textFormat=plainText&part=snippet" + "&maxResults=100"
                    + "&parentId=" + parentId 
                    + PAGE_TOKEN;
    
    Connection.Response response;
    try {
       response = Jsoup.connect(urlStr)
                       .method(Connection.Method.GET)
                       .referrer("https://cs.nyu.edu")
                       .ignoreContentType(true)
                       .maxBodySize(Integer.MAX_VALUE)
                       .execute();
    } catch (IOException e) {
      System.err.println(
          "Network error when retrieving the page: " + urlStr + ".");
      System.err.println(e.getMessage());
      return null;
    }
    
    JsonParser parser = new JsonParser();
    JsonObject rootObj = parser.parse(response.body()).getAsJsonObject();
    JsonArray commentsArrayObj = rootObj.getAsJsonArray("items");
    
    String nextPageToken = null;
    try {
      nextPageToken = rootObj.get("nextPageToken").getAsString();
    } catch (NullPointerException e) {
      // Do nothing
    }
    
    return new CommentsPage(commentsArrayObj, nextPageToken);
  }
  
  static class Comment {
    private String commentId;
    private String parentId;
    private String userId;
    private String userName;
    private String profilePicture;
    private String videoId;
    private String commentText;
    private String publishTime;
    private String updateTime;
    private int likeCount;
    private int replyCount;
    
    private Comment() {
      commentId = "";
      parentId = "";
      userId = "";
      userName = "";
      profilePicture = "";
      videoId = "";
      commentText = "";
      publishTime = "";
      updateTime = "";
      likeCount = 0;
      replyCount = 0;
    }

    public void setVideoId(String videoId) {
      this.videoId = videoId;
    }

    public final String getCommentId() {
      return commentId;
    }

    public final String getParentId() {
      return parentId;
    }

    public final String getUserId() {
      return userId;
    }

    public final String getUserName() {
      return userName;
    }

    public final String getProfilePicture() {
      return profilePicture;
    }

    public final String getVideoId() {
      return videoId;
    }
    
    public final String getCommentText() {
      return commentText;
    }

    public final String getPublishTime() {
      return publishTime;
    }

    public final String getUpdateTime() {
      return updateTime;
    }

    public final int getLikeCount() {
      return likeCount;
    }

    public final int getReplyCount() {
      return replyCount;
    }
    
    /**
     * Factory method for making a top-level comment from parsing JSON
     * 
     * @param jsonObj JSON object for a top-level comment
     * @return a Comment object
     */
    public static Comment parseTopLevelComment(JsonObject jsonObj) {
      Comment ret = new Comment();
      
      ret.commentId = jsonObj.get("id").getAsString();
      ret.userName = jsonObj.get("snippet").getAsJsonObject()
                            .get("topLevelComment").getAsJsonObject()
                            .get("snippet").getAsJsonObject()
                            .get("authorDisplayName").getAsString();
      ret.profilePicture = jsonObj.get("snippet").getAsJsonObject()
                                  .get("topLevelComment").getAsJsonObject()
                                  .get("snippet").getAsJsonObject()
                                  .get("authorProfileImageUrl").getAsString();
      ret.userId = jsonObj.get("snippet").getAsJsonObject()
                          .get("topLevelComment").getAsJsonObject()
                          .get("snippet").getAsJsonObject()
                          .get("authorChannelId").getAsJsonObject()
                          .get("value").getAsString();
      ret.commentText = jsonObj.get("snippet").getAsJsonObject()
                               .get("topLevelComment").getAsJsonObject()
                               .get("snippet").getAsJsonObject()
                               .get("textDisplay").getAsString();
      ret.publishTime = jsonObj.get("snippet").getAsJsonObject()
                               .get("topLevelComment").getAsJsonObject()
                               .get("snippet").getAsJsonObject()
                               .get("publishedAt").getAsString();
      ret.updateTime = jsonObj.get("snippet").getAsJsonObject()
                              .get("topLevelComment").getAsJsonObject()
                              .get("snippet").getAsJsonObject()
                              .get("updatedAt").getAsString();
      ret.likeCount = jsonObj.get("snippet").getAsJsonObject()
                             .get("topLevelComment").getAsJsonObject()
                             .get("snippet").getAsJsonObject()
                             .get("likeCount").getAsInt();
      ret.replyCount = jsonObj.get("snippet").getAsJsonObject()
                              .get("totalReplyCount").getAsInt();
      // Optional Fields
      // If a comment is on a channel instead of a video, there would be no videoId.
      try {
        ret.videoId = jsonObj.get("snippet").getAsJsonObject()
                             .get("videoId").getAsString();
      } catch (NullPointerException e) {
        // Do nothing
      }
      
      return ret;
    }
    
    /**
     * Factory method for making a reply comment from parsing JSON
     * 
     * @param jsonObj JSON object for a reply comment
     * @return a Comment object
     */
    public static Comment parseReplyComment(JsonObject jsonObj) {
      Comment ret = new Comment();
      
      ret.commentId = jsonObj.get("id").getAsString();
      ret.userName = jsonObj.get("snippet").getAsJsonObject()
                            .get("authorDisplayName").getAsString();
      ret.profilePicture = jsonObj.get("snippet").getAsJsonObject()
                                  .get("authorProfileImageUrl").getAsString();
      ret.userId = jsonObj.get("snippet").getAsJsonObject()
                          .get("authorChannelId").getAsJsonObject()
                          .get("value").getAsString();
      ret.commentText = jsonObj.get("snippet").getAsJsonObject()
                               .get("textDisplay").getAsString();
      ret.publishTime = jsonObj.get("snippet").getAsJsonObject()
                               .get("publishedAt").getAsString();
      ret.updateTime = jsonObj.get("snippet").getAsJsonObject()
                              .get("updatedAt").getAsString();
      ret.likeCount = jsonObj.get("snippet").getAsJsonObject()
                             .get("likeCount").getAsInt();
      ret.parentId = jsonObj.get("snippet").getAsJsonObject()
                            .get("parentId").getAsString();
      
      return ret;
    }
    
  }

  /**
   * Retrieve the information of a video from youtube.com. 
   * 
   * @param videoId videoId.
   * @return JSON object containing video information
   */
  private static JsonObject downloadVideoInfo(String videoId) {
    String urlStr = URL_BASE 
                    + "/videos" 
                    + "?key=" + API_KEY
                    + "&part=snippet" 
                    + "&fields=items(id%2Csnippet)"
                    + "&id=" + videoId;
    
    Connection.Response response;
    try {
       response = Jsoup.connect(urlStr)
                       .method(Connection.Method.GET)
                       .referrer("https://cs.nyu.edu")
                       .ignoreContentType(true)
                       .maxBodySize(Integer.MAX_VALUE)
                       .execute();
    } catch (IOException e) {
      System.err.println(e);
      return null;
    }
    
    try {
      JsonParser parser = new JsonParser();
      JsonObject rootObj = parser.parse(response.body()).getAsJsonObject();
      JsonObject videoJson = rootObj.getAsJsonArray("items")
                                           .get(0)
                                           .getAsJsonObject();
      return videoJson;
    } catch (NullPointerException e) {
      System.err.println(e);
      return null;
    }
  }
  
  static class Video {
    private String id;
    private String title;
    private String thumbnail;
    private String channelId;
    private String channelTitle;
    
    private Video() {
      this.id = "";
      this.title = "";
      this.thumbnail = "";
      this.channelId = "";
      this.channelTitle = "";
    }
    
    private Video(String id, String title, String thumbnail, String channelId, String channelTitle) {
      this.id = id;
      this.title = title;
      this.thumbnail = thumbnail;
      this.channelId = channelId;
      this.channelTitle = channelTitle;
    }
    
    /**
     * @return the id
     */
    public String getId() {
      return id;
    }
    
    /**
     * @return the title
     */
    public String getTitle() {
      return title;
    }
    
    /**
     * @return the thumbnail
     */
    public String getThumbnail() {
      return thumbnail;
    }

    /**
     * @return the channelId
     */
    public String getChannelId() {
      return channelId;
    }

    /**
     * @return the channelTitle
     */
    public String getChannelTitle() {
      return channelTitle;
    }
    
    /**
     * Factory method for making a Video object from parsing JSON
     * 
     * @param jsonObj JSON object for a video
     * @return a Video object
     */
    public static Video parseVideoInfo(JsonObject videoJson) {
      try {
        String videoId = videoJson.get("id")
                           .getAsString();
        String title = videoJson.getAsJsonObject("snippet")
                         .get("title")
                         .getAsString();
        String thumbnail = videoJson.getAsJsonObject("snippet")
                             .getAsJsonObject("thumbnails")
                             .getAsJsonObject("default")
                             .get("url")
                             .getAsString();
        String channelId = videoJson.getAsJsonObject("snippet")
                             .get("channelId")
                             .getAsString();
        String channelTitle = videoJson.getAsJsonObject("snippet")
                                .get("channelTitle")
                                .getAsString();
        
        return new Video(videoId, title, thumbnail, channelId, channelTitle);
      } catch (NullPointerException e) {
        System.err.println(e);
        return new Video();
      }
    }
  }
  
  /**
   * Retrieve information about a video from a videoId. 
   * 
   * This method will check against a cache first. If the videoId is not in the cache, it will
   * attempt to download the information from Youtube. 
   * 
   * Note the method will return an Video object with all empty string if the videoId is null or 
   * empty. This CAN happen normally when a comment is associated with a channel instead of a video.
   * (Yes, there are comments on channels on Youtube; you can see them under the 
   * "DISCUSSION" tab if a channel allows it; most big channels don't allow though.)
   * 
   * @param videoId Video ID.
   * @param videoCache Cache of video information.
   * @return Video object containing information of the video.
   */
  private static Video getVideoInfo(String videoId, Map<String, Video> videoCache) {
    // Be careful videoId can be null or empty! 
    if (videoId == null || videoId.isEmpty()) {
      return new Video();
    }
    
    // Cache hit
    if (videoCache.containsKey(videoId)) {
      return videoCache.get(videoId);
    }
    
    // Not in cache; has to download over the internet
    JsonObject videoJson = downloadVideoInfo(videoId);
    Video videoInfo = Video.parseVideoInfo(videoJson);
    videoCache.put(videoId, videoInfo);
    return videoInfo;
  }
  
  private static void addDoc(IndexWriter indexWriter, Comment comment, Video videoInfo)
                                                                         throws IOException {
    Document doc = new Document();
    doc.add(new StringField("commentId", comment.getCommentId(), Field.Store.YES));
    doc.add(new StringField("parentId", comment.getParentId(), Field.Store.YES));
    doc.add(new StringField("userId", comment.getUserId(), Field.Store.YES));
    doc.add(new StringField("videoId", comment.getVideoId(), Field.Store.YES));
    doc.add(new TextField("userName", comment.getUserName(), Field.Store.YES));
    doc.add(new TextField("commentText", comment.getCommentText(), Field.Store.YES));
    doc.add(new StoredField("profilePicture", comment.getProfilePicture()));
    doc.add(new StoredField("likeCount", comment.getLikeCount()));
    doc.add(new StoredField("replyCount", comment.getReplyCount()));
    // TODO add String publishTime, String updateTime
    doc.add(new TextField("videoTitle", videoInfo.getTitle(), Field.Store.YES));
    doc.add(new StoredField("videoThumbnail", videoInfo.getThumbnail()));
    doc.add(new StringField("channelId", videoInfo.getChannelId(), Field.Store.YES));
    doc.add(new TextField("channelTitle", videoInfo.getChannelTitle(), Field.Store.YES));
    

    Term key = new Term("commentId", comment.getCommentId());
    indexWriter.updateDocument(key, doc); // This method checks for the key first to avoid duplicate
  }
  
  private void initialize() {
    try {
      index = FSDirectory.open(indexDir.toPath());
    } catch (IOException e) {
      System.err.println("Error opening index directory " + indexDir);
      e.printStackTrace();
    }
    analyzer = new StandardAnalyzer();
    config = new IndexWriterConfig(analyzer);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND); // Append to existing index
  }
  
  /**
   * Build or update the index for all comments within a "scope" (a video or a channel).
   * 
   * The method first downloads all pages of top-level comments until there is no more pages, 
   * then for each top-level comment, downloads all pages of its reply comments; meanwhile, 
   * for each downloaded top-level comment and reply comment, parse their content as well as
   * other attributes and add/update to the index.
   * 
   * @param scope type of the scope
   * @param scopeId ID of scope (e.g. VideoId for VIDEO scope, ChannelId for CHANNEL scope)
   */
  public void buildCommentIndex(Scope scope, String scopeId) {
    initialize();
    
    // Make a hash map cache for video information
    Map<String, Video> videoCache = new HashMap<String, Video>();
    
    // Main indexer loop
    try (IndexWriter indexWriter = new IndexWriter(index, config)) {
      int pageNum = 1;
      String topLevelPageToken = null;
      do {
        // Loop top-level pages
        System.out.print("Indexing top-level page " + pageNum + "...");
        
        CommentsPage topLevelPage = downloadTopLevelCommentsPage(scope, scopeId, topLevelPageToken);
        JsonArray topLevelComments = topLevelPage.getComments();
        for (int i = 0; i < topLevelComments.size(); ++i) {
          // Loop comment threads (top-level comments)
          
          System.out.printf("%2d%%", (int)((float) i / (topLevelComments.size()) * 100));
          Comment comment = Comment.parseTopLevelComment(topLevelComments.get(i).getAsJsonObject());
          String videoId = comment.getVideoId();
          Video videoInfo = getVideoInfo(videoId, videoCache); 
          addDoc(indexWriter, comment, videoInfo);
          String parentId = comment.getCommentId();
          
          if (comment.getReplyCount() > 0) {
            String replyPageToken = null;
            do {
              // Loop reply pages
              CommentsPage replyPage = downloadReplyCommentsPage(parentId, replyPageToken);
              JsonArray replyComments = replyPage.getComments();
              for (int j = 0; j < replyComments.size(); ++j) {
                // Loop replies
                comment = Comment.parseReplyComment(replyComments.get(j).getAsJsonObject());
                comment.setVideoId(videoId);
                addDoc(indexWriter, comment, videoInfo);
              } // END FOR (loop replies)
              replyPageToken = replyPage.getNextPageToken();
            } while (replyPageToken != null);
          }
          System.out.print("\b\b\b");
        } // END FOR (loop comment threads)
        topLevelPageToken = topLevelPage.getNextPageToken();
        ++pageNum;
        System.out.println("DONE");
      } while (topLevelPageToken != null);
      
    } catch (IOException e) {
      System.err.println("Error making index.");
      e.printStackTrace();
      return;
    }
  }
  
  public static void main(String[] args) {
    YoutubeIndexer youtubeIndexer = new YoutubeIndexer();
    youtubeIndexer.parseArgs(args);
    if (youtubeIndexer.apiKey != null) API_KEY = youtubeIndexer.apiKey;
    Scope scope = null;
    if (youtubeIndexer.isVideo) {
      scope = Scope.VIDEO;
    } else if (youtubeIndexer.isChannel) {
      scope = Scope.CHANNEL;
    } else {
      // This part should be unreachable if the code is right.
      System.err.println("Unknown scope.");
      System.exit(-1);
    }
    youtubeIndexer.buildCommentIndex(scope, youtubeIndexer.id);
    System.out.println("Indexing all finished.");
  }

}
