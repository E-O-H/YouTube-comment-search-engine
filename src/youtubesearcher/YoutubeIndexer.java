package youtubesearcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 
 * 
 * @author Chenyang Tang
 *
 */
public class YoutubeIndexer {
  
  Path indexDir = Paths.get("index");
  
  private static final String URL_BASE = "https://www.googleapis.com/youtube/v3";
  private static final String API_KEY = "AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc";
  
  /*
   * Lucene indexer internal objects
   */
  private StandardAnalyzer analyzer; // analyzer for tokenizing text
  private Directory index;           // the index
  
  enum Scope {
    VIDEO, CHANNEL;
  }
  
  /**
   * Download the comments of a video
   * 
   * @param scopeId ID of scope (e.g. VideoId for VIDEO scope, ChannelId for CHANNEL scope)
   * @return JSON object of the comments
   */
  public static JsonArray downloadComments(Scope scope, String scopeId) {
    final String SCOPE_FILTER;
    switch (scope) {
      case VIDEO:
        SCOPE_FILTER = "&VideoId=";
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
      commentId = null;
      parentId = null;
      userId = null;
      userName = null;
      profilePicture = null;
      videoId = null;
      commentText = null;
      publishTime = null;
      updateTime = null;
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
     * Factory method for making a top-level comment
     * 
     * @param jsonObj JSON object for a top-level comment
     * @return Comment object
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
     * Factory method for making a reply comment
     * 
     * @param json JSON object for a reply comment
     * @return Comment object
     */
    public static Comment parseReplyComment(JsonObject json) {
      Comment ret = new Comment();
      
      
      return ret;
    }
    
  }
  
  private static void addDoc(IndexWriter indexWriter, String commentId, String parentId, 
                             String userId, String userName, String profilePicture, 
                             String videoId, int likeCount, int replyCount, 
                             String comment, String publishTime, String updateTime) throws IOException {
    Document doc = new Document();
    
    
    
    
    //doc.add(new TextField("title", title, Field.Store.YES));
    //doc.add(new TextField("text", text, Field.Store.YES));
    //doc.add(new StringField("path", path, Field.Store.YES));
    
    //Term key = new Term("id", id);
    //indexWriter.addDocument(doc);
  }
  
  private void initialize() {
    try {
      index = FSDirectory.open(indexDir);
    } catch (IOException e) {
      System.err.println("Error opening index directory" + indexDir);
      e.printStackTrace();
    }
    analyzer = new StandardAnalyzer();
  }
  
  public void buildCommentIndex(Scope scope, String scopeId) {
    initialize();
    JsonArray topLevelComments = downloadComments(scope, scopeId);
    for (int i = 0; i < topLevelComments.size() - 1; ++i) {
      Comment comment = Comment.parseTopLevelComment(topLevelComments.get(i).getAsJsonObject());
      
      
      
    }
  }
  
  public static void main(String[] args) {
    YoutubeIndexer youtubeIndexer = new YoutubeIndexer();
    youtubeIndexer.buildCommentIndex(Scope.CHANNEL, "UC6ReSe57JF9ju3JPYRwxTUQ");
  }

}
