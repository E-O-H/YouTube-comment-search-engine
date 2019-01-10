package youtubesearcher;

import java.io.IOException;

import org.apache.lucene.document.Document;
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
  
  private static final String URL_BASE = "https://www.googleapis.com/youtube/v3";
  private static final String API_KEY = "AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc";
  private static final String ACCESS_TOKEN = "636583674247-h0ku9u1erb78r7d7bdg4c1bf0ckj8v70.apps.googleusercontent.com";
  
  enum Scope {
    VIDEO, CHANNEL;
  }
  
  /**
   * Download the comments of a video
   * 
   * @param videoId video id
   * @return JSON object of the comments
   */
  public static JsonArray downloadComments(Scope scope, String videoId) {
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
                    + videoId + "&maxResults=100";
    
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
  
  /**
   * 
   */
  public YoutubeIndexer() {
    // TODO Auto-generated constructor stub
  }
  
  public static void main(String[] args) {
    System.out.println(downloadComments(Scope.CHANNEL, "UC6ReSe57JF9ju3JPYRwxTUQ"));
  }

}
