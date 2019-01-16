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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;


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
  private String usernameString;
  
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
  
  @Option(name = "-page", aliases = "-p",
      usage = "Page number of the search results to output.")
  private int page = 1; 
  
  @Option(name = "-max", aliases = "-m",
      usage = "Maximum number of search results to output for each page.")
  private int hitsPerPage = 20; 
  
  @Option(name = "-webpage-URL", aliases = "-w",
      usage = "URL of the search engine webpage; needed to correctly render links")
  private String webpageUrl; 
  
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
    if (usernameString != null && ! usernameString.isEmpty()) {
      Query userNameQuery = new QueryParser("userName", analyzer).parse(usernameString);
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
      ScoreDoc[] results = docs.scoreDocs;
      
      int numTotalHits = docs.totalHits;

      if (page > 1 && (page - 1) * hitsPerPage < numTotalHits) {
        for (int i = 0; i < page - 1; ++i) {
          ScoreDoc lastHit = results[results.length - 1];
          docs = searcher.searchAfter(lastHit, finalQuery, hitsPerPage);
          results = docs.scoreDocs;
        }
      }
      
      System.out.println("<h2>Results for query <u>" 
          + commentQueryString 
          + "</u></h2>");
      
      outputPagination(numTotalHits);
      outputResults(results, page, finalQuery);
      outputPagination(numTotalHits);
      
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
   * @param results The hits to display
   * @param page The page number (used for the number before each result)
   * @param query Query object (used for highlighting search terms in results)
   * @throws IOException 
   */
  private void outputResults(ScoreDoc[] results, int page, Query query) throws IOException {
    if (searcher.getIndexReader().maxDoc() == 0) {
      System.err.println("No document in the index!");
      return;
    }
    
    for(int i = 0; i < results.length; ++i) {
        int docId = results[i].doc;
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
        
        // The font is the same as that used by Youtube comments
        String html = "<p style='font-family:Roboto,Arial,sans-serif;'>"
                        + "<span style='font-size:1.3rem;font-weight:bold;'>"
                          + "<i>" 
                            + (i + 1 + (page - 1) * hitsPerPage) 
                          + "</i>. " 
                          + "<a href=\"https://www.youtube.com/channel/"
                            + doc.get("userId")
                          + "\">"
                            + "<img src=\""
                              + doc.get("profilePicture")
                            + "\" width=20 height=20>"
                            + doc.get("userName") 
                          + "</a>"
                        + "</span>"
                        + " commented on video<br>"
                        + "<table style='font-family:Roboto,Arial,sans-serif;'>"
                        + "<td>"
                          + "<a href=\"https://www.youtube.com/watch?v="
                            + doc.get("videoId")
                          + "\">"
                            + "<img src=\""
                              + doc.get("videoThumbnail")
                            + "\">"
                          + "</a>"
                        + "</td>"
                        + "<td>"
                          + "<a href=\"https://www.youtube.com/watch?v="
                            + doc.get("videoId")
                          + "\">"
                            + "<span style='font-size:1.2rem;font-weight:bold;'>"
                              + doc.get("videoTitle")
                            + "</span>"
                          + "</a>"
                          + "<span style='font-size:0.7rem;font-weight:bold;'>" 
                            + "<br>  from channel: "
                            + "<a href=\"https://www.youtube.com/channel/"
                              + doc.get("channelId")
                            + "\">"
                              + doc.get("channelTitle")
                            + "</a><br>"
                          + "</span>"
                        + "<a href=\"https://www.youtube.com/watch?v="
                          + doc.get("videoId")
                          + "&lc="
                          + doc.get("commentId")
                        + "\">"
                          + "<span style='font-size:0.9rem;margin-left:2em;'>\"" 
                            + highlightedText
                          + "\"</span>"
                        + "</a>"
                      + "</td>"
                      + "</table>"
                      + "</p>";
          
        System.out.println(html);
    }
  }
  
  /**
   * Prints out the interactive pagination in HTML format
   * 
   * @param numTotalHits Total number of results
   */
  private void outputPagination(int numTotalHits) {
    final int PEEK_RANGE = 9;
    int lastPage = numTotalHits / hitsPerPage + (numTotalHits % hitsPerPage != 0 ? 1 : 0);
    
    String html = "";
    if (lastPage != 1) {
      html += "&nbsp&nbsp";
      if (page != 1) {
        html += printPageLink(1, "First") + "&nbsp&nbsp"
                + printPageLink(page - 1, "Prev");
      }
      
      if (page > 1 + PEEK_RANGE) {
        html += " ...";
      } else {
        html += "&nbsp&nbsp";
      }
      
      for (int i = Math.max(page - PEEK_RANGE, 1); i < page; ++i) {
        html += printPageLink(i, String.valueOf(i)) + "&nbsp&nbsp";
      }
      
      html += String.valueOf(page);
      
      for (int i = page + 1; i < Math.min(page + PEEK_RANGE, lastPage); ++i) {
        html += "&nbsp&nbsp" + printPageLink(i, String.valueOf(i));
      }

      if (page < lastPage - PEEK_RANGE) {
        html += "... ";
      } else {
        html += "&nbsp&nbsp";
      }
      
      if (page != lastPage) {
        html += printPageLink(page + 1, "Next") + "&nbsp&nbsp"
                + printPageLink(lastPage, "Last");
                
      }
    }
    
    html += "<br><span style='margin-left:1em'>"
             + "Displaying results " + (hitsPerPage * (page - 1) + 1) + " ~ "
             + (hitsPerPage * (page - 1) + hitsPerPage)
             + " (page " + page + " of " + lastPage + ")"
             + "<br></span>";
        
    System.out.println(html);
  }
  
  /**
   * Prints the HTML hyperlink for a page
   * 
   * @param page The page number to link to.
   * @param anchorText The anchor text to display for the link.
   * @return HTML code of a link.
   */
  private String printPageLink(int pageNumber, String anchorText) {
    String html;
    html = "<a href=\"" 
           + webpageUrl 
           + "?page=" + pageNumber
           + "&max=" + hitsPerPage
           + "&commentQuery=" + commentQueryString
           + "&usernameQuery=" + usernameString
           + "&userIdQuery=" + userIdString
           + "&videoTitleQuery=" + videoTitleString
           + "&videoIdQuery=" + videoIdString
           + "&channelTitleQuery=" + channelTitleString
           + "&channelIdQuery=" + channelIdString
           + "\">"
           + anchorText
           + "</a>";
    return html;
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
