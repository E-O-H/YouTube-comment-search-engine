package youtubesearcher;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
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

  @Option(name = "-query", aliases = "-q", required = true, 
          usage = "The search query terms string. Required option.")
  private String queryString;
  
  @Option(name = "-max", aliases = "-m", required = false, 
      usage = "Maximum number of search results to output.")
  private int hitsPerPage = 20; 

  @Option(name = "-help", aliases = "-h", required = false, 
          usage = "Print help text.")
  private boolean printHelp = false;
  
  /*
   * Lucene retriever internal objects
   */
  private StandardAnalyzer analyzer; // analyzer for tokenizing text
  private Directory index;           // the index
  private IndexReader reader;        // reader object
  private IndexSearcher searcher;    // searcher object
  private TopDocs docs;              // top docs
  
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
   * Search the query string.
   */
  private int search() {
    initialize();
    
    // Build the Query object.
    Query query;
    try {
      // Default search field is "commentText".
      // Remember the analyzer must be the same one used when building the index.
      query = new QueryParser("commentText", analyzer).parse(queryString);
    } catch (ParseException e) {
      System.err.println("Error parsing the query string: \"" + queryString + "\"");
      e.printStackTrace();
      return 1;
    }

    // Search
    try {
      reader = DirectoryReader.open(index);
      searcher = new IndexSearcher(reader);
      docs = searcher.search(query, hitsPerPage);
      
      outputResults();
    } catch (IOException e) {
      System.err.println("Error opening index.");
      e.printStackTrace();
      return 2;
    }
    
    return 0;
  }
  
  /**
   * Output results as an HTML snippet
   * @throws IOException 
   */
  private void outputResults() throws IOException {
    if (searcher.getIndexReader().maxDoc() == 0) {
      System.err.println("No document in the index!");
      return;
    }
    
    System.out.println("<h3>Results for query <u>" 
                       + queryString 
                       + "</u></h3>");
    
    ScoreDoc[] hits = docs.scoreDocs;
    for(int i = 0; i < hits.length; ++i) {
        int docId = hits[i].doc;
        Document doc = searcher.doc(docId);
        System.out.println("<p><b><i>" 
                           + (i + 1) 
                           + "</i>. " 
                           + doc.get("userName") 
                           + "</b><br><span style='margin-left:3em'>" 
                           + doc.get("commentText") + "</span></p>");
    }
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
    final YoutubeRetriever retriever = new YoutubeRetriever();
    int status;
    status = retriever.parseArgs(args);
    if (status != 0) System.exit(status);
    status = retriever.search();
    if (status != 0) System.exit(status);
  }

}
