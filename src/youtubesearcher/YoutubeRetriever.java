package youtubesearcher;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
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
 * Retriever for searching a Youtube comments index
 * 
 * @author Chenyang Tang
 *
 */
public class YoutubeRetriever {
  
  /*
   * command-line arguments for the entry point
   */
  @Option(name = "-index", aliases = "-i", required = true, 
          usage = "Path to the directory of the index files to be searched. Required option.")
  private File indexDir;

  @Option(name = "-query", aliases = "-q", 
          usage = "The search query terms string. "
                  + "Can be ommited, e.g. to search for all comments from a user.")
  private String commentQueryString;
  
  @Option(name = "-username", aliases = "-un",
      usage = "Usernames to filter the search result. Double quote a username for exact match.")
  private String userNameString;
  
  @Option(name = "-userId", aliases = "-ui",
      usage = "A list of user IDs to filter the search result. Separated by space.")
  private String userIdString;
  
  @Option(name = "-videoId", aliases = "-vi",
      usage = "A list of video IDs to filter the search result. Separated by space.")
  private String videoIdString;
  
  @Option(name = "-max", aliases = "-m",
      usage = "Maximum number of search results to output.")
  private int hitsPerPage = 20; 

  @Option(name = "-api-key", aliases = "-k", 
      usage = "Specify an API key to use. A built-in default one is used if not specified.")
  private String apiKey;
  
  @Option(name = "-help", aliases = "-h", help = true,
          usage = "Print help text.")
  private boolean printHelp = false;
  
  private static final String URL_BASE = "https://www.googleapis.com/youtube/v3";
  private static String API_KEY = "AIzaSyDF7H_kAHJsIhijiIKU9cxZuK7sforZnIc";
  
  /*
   * Lucene retriever internal objects
   */
  private StandardAnalyzer analyzer; // analyzer for tokenizing text
  private Directory index;           // the index
  private IndexReader reader;        // reader object
  private IndexSearcher searcher;    // searcher object
  private BooleanQuery finalQuery;   // constructed query object
  private TopDocs docs;              // search result
  
  private void initialize() {
    try {
      // Open a File-System-Index-Directory for use 
      // (i.e. an index on disk, as opposed to one in memory).
      // FSDirectory.open() chooses the best FSDirectory implementation automatically 
      // given the environment and the known limitations of each implementation.
      index = FSDirectory.open(indexDir.toPath());
    } catch (IOException e) {
      System.err.println("Error opening index directory" + indexDir);
      e.printStackTrace();
    }
    analyzer = new StandardAnalyzer();
  }
  
  /**
   * Build the final Query object.
   * 
   * @return The final combined Query object (a BooleanQuery object).
   * @throws ParseException Exception in parsing query.
   */
  private BooleanQuery buildQuery() throws ParseException {
    BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
    
    // Comment query
    if (commentQueryString != null) {
      Query commentQuery = new QueryParser("commentText", analyzer).parse(commentQueryString);
      booleanQueryBuilder.add(commentQuery, Occur.MUST);
    }
    
    // Username query
    if (userNameString != null) {
      Query userNameQuery = new QueryParser("userName", analyzer).parse(userNameString);
      booleanQueryBuilder.add(userNameQuery, Occur.MUST);
    }
    // UserId query
    if (userIdString != null) {
      Query userIdQuery = new QueryParser("userId", analyzer).parse(userIdString);
      booleanQueryBuilder.add(userIdQuery, Occur.MUST);
    }
    // VideoId query
    if (videoIdString != null) {
      Query videoIdQuery = new QueryParser("videoId", analyzer).parse(videoIdString);
      booleanQueryBuilder.add(videoIdQuery, Occur.MUST);
    }
    
    return booleanQueryBuilder.build();
  }
  
  /**
   * Search the query string.
   * 
   * @return status code (0 for success).
   */
  private int search() {
    initialize();
    
    // Build the Query object.
    try {
      finalQuery = buildQuery();
    } catch (ParseException e) {
      System.err.println("Error parsing the query string: \"" + commentQueryString + "\"");
      e.printStackTrace();
      return 1;
    }

    // Search
    try {
      reader = DirectoryReader.open(index);
      searcher = new IndexSearcher(reader);
      docs = searcher.search(finalQuery, hitsPerPage);
      
      outputResults(finalQuery);
    } catch (IOException e) {
      System.err.println("Error opening index.");
      e.printStackTrace();
      return 2;
    }
    
    return 0;
  }
  
  /**
   * Output results as an HTML snippet
   * 
   * @param query Query object (used for highlighting result)
   * @throws IOException 
   */
  private void outputResults(Query query) throws IOException {
    if (searcher.getIndexReader().maxDoc() == 0) {
      System.err.println("No document in the index!");
      return;
    }
    
    System.out.println("<h3>Results for query <u>" 
                       + commentQueryString 
                       + "</u></h3>");
    
    ScoreDoc[] hits = docs.scoreDocs;
    for(int i = 0; i < hits.length; ++i) {
        int docId = hits[i].doc;
        Document doc = searcher.doc(docId);
        
        String highlightedText;
        if (commentQueryString != null) {
          try {
            highlightedText = getHighlightedField(query, analyzer, 
                                                  "commentText", doc.get("commentText"));
          } catch (InvalidTokenOffsetsException e) {
            highlightedText = doc.get("commentText");
          }
        } else {
          // Don't call getHighlightedField() if there is no search on the field "commentText"!
          // See the NOTE section of the Javadoc of getHighlightedField().
          highlightedText = doc.get("commentText");
        }
        
        Video video = getVideoInfo(doc.get("videoId"));
        
        System.out.println("<p><b><i>" 
                           + (i + 1) 
                           + "</i>. " 
                           + doc.get("userName") 
                           + " in video: "
                           + video.title
                           + "</b><br><span style='margin-left:3em'>" 
                           + highlightedText
                           + "</span></p>");
    }
  }
  
  /**
   * Retrieve the information of a video from youtube.com. 
   * 
   * @param videoId videoId.
   * @return Video object containing video information
   */
  Video getVideoInfo(String videoId) {
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
                       .ignoreContentType(true)
                       .maxBodySize(Integer.MAX_VALUE)
                       .execute();
    } catch (IOException e) {
      System.err.println(e);
      return new Video();
    }
    
    JsonParser parser = new JsonParser();
    JsonObject rootObj = parser.parse(response.body()).getAsJsonObject();
    
    String title = null;
    String thumbnail = null;
    String channelId = null;
    String channelTitle = null;
    try {
      JsonObject videoSnippetJson = rootObj.getAsJsonArray("items")
                                           .get(0)
                                           .getAsJsonObject()
                                           .getAsJsonObject("snippet");
      title = videoSnippetJson.get("title")
                              .getAsString();
      thumbnail = videoSnippetJson.getAsJsonObject("thumbnails")
                                  .getAsJsonObject("default")
                                  .get("url")
                                  .getAsString();
      channelId = videoSnippetJson.get("channelId")
                                  .getAsString();
      channelTitle = videoSnippetJson.get("channelTitle")
                                     .getAsString();
    } catch (NullPointerException e) {
      System.err.println(e);
      return new Video();
    }
    
    return new Video(videoId, title, thumbnail, channelId, channelTitle);
  }
  
  static class Video {
    private String id;
    private String title;
    private String thumbnail;
    private String channelId;
    private String channelTitle;
    
    public Video() {
      this.id = null;
      this.title = null;
      this.thumbnail = null;
      this.channelId = null;
      this.channelTitle = null;
    }
    
    public Video(String id, String title, String thumbnail, String channelId, String channelTitle) {
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
  }
  
  /**
   * Mark highlights in a field with a given query.
   * 
   * Note the query must have a clause that searches the given field, otherwise the returned result
   * will be null instead of the original content without highlight (even if there are clauses that
   * search for other fields in the query)
   * 
   * @param query The Query object.
   * @param analyzer The Analyzer object for the query.
   * @param fieldName Name of the field to search for highlights.
   * @param fieldValue Content of the field to search for highlights.
   * @return Highlight-modified content of the field.
   * @throws IOException 
   * @throws InvalidTokenOffsetsException
   */
  private static String getHighlightedField(Query query, Analyzer analyzer, 
                                            String fieldName, String fieldValue) 
                                         throws IOException, InvalidTokenOffsetsException {
    Formatter formatter = new SimpleHTMLFormatter("<b>", "</b>");
    QueryScorer queryScorer = new QueryScorer(query);
    Highlighter highlighter = new Highlighter(formatter, queryScorer);
    highlighter.setTextFragmenter(new SimpleSpanFragmenter(queryScorer, Integer.MAX_VALUE));
    highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
    return highlighter.getBestFragment(analyzer, fieldName, fieldValue);
}
  
  private int parseArgs(String[] args) {
    final CmdLineParser args4jCmdLineParser = new CmdLineParser(this);
    try {
      args4jCmdLineParser.parseArgument(args);
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
  
  /**
   * Retriever Entry point.
   * 
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    final YoutubeRetriever youtubeRetriever = new YoutubeRetriever();
    if (youtubeRetriever.apiKey != null) API_KEY = youtubeRetriever.apiKey;
    int status;
    status = youtubeRetriever.parseArgs(args);
    if (status != 0) System.exit(status);
    status = youtubeRetriever.search();
    if (status != 0) System.exit(status);
  }

}
