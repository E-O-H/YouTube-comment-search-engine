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
    for (int i = 0; i < topLevelComments.size(); ++i) {
      String commentId = topLevelComments.get(i).getAsJsonObject()
                                         .get("id").getAsString();
      String videoId = topLevelComments.get(i).getAsJsonObject()
                                       .get("snippet").getAsJsonObject()
                                       .get("videoId").getAsString();
      String userName = topLevelComments.get(i).getAsJsonObject()
                                        .get("snippet").getAsJsonObject()
                                        .get("topLevelComment").getAsJsonObject()
                                        .get("snippet").getAsJsonObject()
                                        .get("authorDisplayName").getAsString();
      String profilePicture = topLevelComments.get(i).getAsJsonObject()
                                              .get("snippet").getAsJsonObject()
                                              .get("topLevelComment").getAsJsonObject()
                                              .get("snippet").getAsJsonObject()
                                              .get("authorProfileImageUrl").getAsString();
      String userId = topLevelComments.get(i).getAsJsonObject()
                                      .get("snippet").getAsJsonObject()
                                      .get("topLevelComment").getAsJsonObject()
                                      .get("snippet").getAsJsonObject()
                                      .get("authorChannelId").getAsJsonObject()
                                      .get("value").getAsString();
      String comment = topLevelComments.get(i).getAsJsonObject()
                                       .get("snippet").getAsJsonObject()
                                       .get("topLevelComment").getAsJsonObject()
                                       .get("snippet").getAsJsonObject()
                                       .get("textDisplay").getAsString();
      String likeCount = topLevelComments.get(i).getAsJsonObject()
                                         .get("snippet").getAsJsonObject()
                                         .get("topLevelComment").getAsJsonObject()
                                         .get("snippet").getAsJsonObject()
                                         .get("likeCount").getAsString();
      String publishTime = topLevelComments.get(i).getAsJsonObject()
                                           .get("snippet").getAsJsonObject()
                                           .get("topLevelComment").getAsJsonObject()
                                           .get("snippet").getAsJsonObject()
                                           .get("publishedAt").getAsString();
      String updateTime = topLevelComments.get(i).getAsJsonObject()
                                          .get("snippet").getAsJsonObject()
                                          .get("topLevelComment").getAsJsonObject()
                                          .get("snippet").getAsJsonObject()
                                          .get("updatedAt").getAsString();
      String replyCount = topLevelComments.get(i).getAsJsonObject()
                                          .get("snippet").getAsJsonObject()
                                          .get("totalReplyCount").getAsString();
    }
  }
  
  public static void main(String[] args) {
    YoutubeIndexer youtubeIndexer = new YoutubeIndexer();
    youtubeIndexer.buildCommentIndex(Scope.CHANNEL, "UC6ReSe57JF9ju3JPYRwxTUQ");
  }

}
