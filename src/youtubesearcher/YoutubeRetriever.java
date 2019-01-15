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
  
  @Option(name = "-video-title", aliases = "-vt",
      usage = "Video titles to filter the search result. Double quote a title for exact match.")
  private String videoTitleString;
  
  @Option(name = "-videoId", aliases = "-vi",
      usage = "A list of video IDs to filter the search result. Separated by space.")
  private String videoIdString;
  
  @Option(name = "-channel-title", aliases = "-ct",
      usage = "Channel titles to filter the search result. Double quote a name for exact match.")
  private String channelTitleString;
  
  @Option(name = "-channelId", aliases = "-ci",
      usage = "A list of channel IDs to filter the search result. Separated by space.")
  private String channelIdString;
  
  @Option(name = "-max", aliases = "-m",
      usage = "Maximum number of search results to output.")
  private int hitsPerPage = 20; 
  
  @Option(name = "-help", aliases = "-h", help = true,
          usage = "Print help text.")
  private boolean printHelp = false;
  
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
    if (commentQueryString != null && ! commentQueryString.isEmpty()) {
      Query commentQuery = new QueryParser("commentText", analyzer).parse(commentQueryString);
      booleanQueryBuilder.add(commentQuery, Occur.MUST);
    }
    // Username query
    if (userNameString != null && ! userNameString.isEmpty()) {
      Query userNameQuery = new QueryParser("userName", analyzer).parse(userNameString);
      booleanQueryBuilder.add(userNameQuery, Occur.MUST);
    }
    // User Id query
    if (userIdString != null && ! userIdString.isEmpty()) {
      Query userIdQuery = new QueryParser("userId", analyzer).parse(userIdString);
      booleanQueryBuilder.add(userIdQuery, Occur.MUST);
    }
    // Video title query
    if (videoTitleString != null && ! videoTitleString.isEmpty()) {
      Query videoTitleQuery = new QueryParser("videoTitle", analyzer).parse(videoTitleString);
      booleanQueryBuilder.add(videoTitleQuery, Occur.MUST);
    }
    // Video Id query
    if (videoIdString != null && ! videoIdString.isEmpty()) {
      Query videoIdQuery = new QueryParser("videoId", analyzer).parse(videoIdString);
      booleanQueryBuilder.add(videoIdQuery, Occur.MUST);
    }
    // Channel title query
    if (channelTitleString != null && ! channelTitleString.isEmpty()) {
      Query channelTitleQuery = new QueryParser("channelTitle", analyzer).parse(channelTitleString);
      booleanQueryBuilder.add(channelTitleQuery, Occur.MUST);
    }
    // Channel Id query
    if (channelIdString != null && ! channelIdString.isEmpty()) {
      Query channelIdQuery = new QueryParser("channelId", analyzer).parse(channelIdString);
      booleanQueryBuilder.add(channelIdQuery, Occur.MUST);
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
        if (commentQueryString != null && ! commentQueryString.isEmpty()) {
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
        
        System.out.println("<p><b><i>" 
                           + (i + 1) 
                           + "</i>. " 
                           + doc.get("userName") 
                           + " on video: "
                           + doc.get("videoTitle")
                           + "</b>"
                           + " from channel: "
                           + doc.get("channelTitle")
                           + "<br><span style='margin-left:3em'>" 
                           + highlightedText
                           + "</span></p>");
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
    int status;
    status = youtubeRetriever.parseArgs(args);
    if (status != 0) System.exit(status);
    status = youtubeRetriever.search();
    if (status != 0) System.exit(status);
  }

}
