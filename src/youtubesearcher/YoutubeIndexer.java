package youtubesearcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
  private static final String API_KEY = "AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc";
  
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
   * Download one page of the top-level comments within a "scope" 
   * (e.g. a "scope" can be a video or a channel).
   * 
   * @param scope type of the scope
   * @param scopeId ID of scope (e.g. VideoId for VIDEO scope, ChannelId for CHANNEL scope)
   * @return JSON object of the comments
   */
  private static JsonArray downloadTopLevelCommentsPage(Scope scope, String scopeId) {
    final String SCOPE_FILTER;
    switch (scope) {
      case VIDEO:
        SCOPE_FILTER = "&videoId=";
        break;
      case CHANNEL:
        SCOPE_FILTER = "&allThreadsRelatedToChannelId=";
        break;
      default:
        System.err.println("Invalid Scope.");
        return null;
    }
    
    String urlStr = URL_BASE + "/commentThreads" + "?key=" + API_KEY 
                    + "&textFormat=plainText&part=snippet" + SCOPE_FILTER
                    + scopeId + "&maxResults=100";
    
    Connection.Response response;
    try {
       response = Jsoup.connect(urlStr)
                       .method(Connection.Method.GET)
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
    return commentsArrayObj;
  }
  
  private static JsonArray downloadReplyCommentsPage() {
    // TODO
    // https://www.googleapis.com/youtube/v3/comments?key=AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc&textFormat=plainText&part=snippet&parentId=UgzV9t-ph7EGpULwDbV4AaABAg
    JsonArray commentsArrayObj = null;
    return commentsArrayObj;
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
     * @param json JSON object for a reply comment
     * @return a Comment object
     */
    public static Comment parseReplyComment(JsonObject json) {
      Comment ret = new Comment();
      
      
      return ret;
    }
    
  }
  
  private static void addDoc(IndexWriter indexWriter, Comment comment) throws IOException {
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
    JsonArray topLevelComments = downloadTopLevelCommentsPage(scope, scopeId);
    
    initialize();
    try (IndexWriter indexWriter = new IndexWriter(index, config)) {
      for (int i = 0; i < topLevelComments.size() - 1; ++i) {
        Comment comment = Comment.parseTopLevelComment(topLevelComments.get(i).getAsJsonObject());
        addDoc(indexWriter, comment);
        // TODO replies
      }
    } catch (IOException e) {
      System.err.println("Error making index.");
      e.printStackTrace();
      return;
    }
  }
  
  public static void main(String[] args) {
    YoutubeIndexer youtubeIndexer = new YoutubeIndexer();
    youtubeIndexer.parseArgs(args);
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
  }

}
