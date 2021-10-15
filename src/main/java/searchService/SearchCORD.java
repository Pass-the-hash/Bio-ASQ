package searchService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.CSVManager;

//import org.apache.commons.lang3.time.StopWatch;
//import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.*;
import parser.Citation;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
//import java.util.Arrays;

public class SearchCORD extends ConceptSearcher {
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static final int hitsMax = 100000; // "search window" search for hitsMax top documents each time.
    private static final int totalHitsMax = 100000; // maximum of hits to take into account as results.

    //private String defaultNamespace = null;
    private IndexReader reader;
    private IndexSearcher searcher;
    private Analyzer analyzer;
    private MultiFieldQueryParser parser;
    private final String name="CORD Searcher";

    private static Directory index;
    private static String directory;
   // private static String query;
    private static ArrayList<String> specialCharacters = new ArrayList<String>();
    private String indexPath;


    public SearchCORD(String indexPath, String directory) throws IOException{
        SearchCORD.directory=directory;
        //SearchCORD.query =query;

        specialCharacters.add("\"");
        specialCharacters.add("(");
        specialCharacters.add(")");
        specialCharacters.add("[");
        specialCharacters.add("]");
        specialCharacters.add("+");
        specialCharacters.add("-");
        specialCharacters.add("?");
        specialCharacters.add("!");
        specialCharacters.add("^");
        specialCharacters.add("~");
        specialCharacters.add("*");
        specialCharacters.add(":");
        specialCharacters.add("AND");
        specialCharacters.add("OR");
        specialCharacters.add("NOT");

        this.setIndexPath(indexPath);


        index=new NIOFSDirectory(Paths.get(getIndexPath()));
       // index=FSDirectory.open(Path.of(indexPath));
        //index=MMapDirectory.open(Paths.get(getIndexPath()));

        this.setAnalyzer(new StandardAnalyzer());

        //this.setSearchFields();

        // extra searcher to search for concepts by id (used by scripts that merge system answers and import them to assessment DB)
        /*this.setIdSearcher(new IndexSearcher(getReader()));
        this.setIdParser(new QueryParser( "id", getAnalyzer()));*/

    }


    @Override
    public String search(String queryString, int page, int docsPerPage) throws IOException {
        //StandardAnalyzer analyzer = new StandardAnalyzer();

        /*StopWatch stopwatch = new StopWatch();
        stopwatch.start();*/

        // 1. create the index
        try {
            this.setReader(DirectoryReader.open(index));
            this.setSearcher(new IndexSearcher(getReader()));
        } catch (Exception e) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);


            IndexWriter w = new IndexWriter(index, config);
            System.out.println("Created writer");
            w.deleteAll();
            w.commit();
            System.out.println("Deleted documents");
            addDoc(w, directory);
        /*addDoc(w, "Lucene in Action", "193398817");
        addDoc(w, "Lucene for Dummies", "55320055Z");
        addDoc(w, "Managing Gigabytes", "55063554A");
        addDoc(w, "The Art of Computer Science", "9900333X");*/

            w.close();
            System.out.println("Added documents");
        }

        // 2. query
        //String querystr=queryString;
        //System.out.println(args[0]);
        // the "title" arg specifies the default field to use
        // when no field is explicitly specified in the query.
        Query query;

        //q= new QueryParser("abstract", analyzer).parse(querystr);
        //
        try {
            queryString = cleanQuery(queryString, specialCharacters);
            query = new QueryParser("abstract", analyzer).parse(queryString);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        System.out.println("Query parsed");

        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String json;
        //Search and calculate the search time in MS
        Date startDate = new Date();
        // Collect hitsMax top Docs
        TopDocs results = getSearcher().search(query, getHitsMax());
        Date endDate = new Date();
        Long timeMS = endDate.getTime() - startDate.getTime();

        System.out.println(results.totalHits);

        // 3. search
        int numTotalHits = (int) results.totalHits;

        //page : [0, maxPage], maxPage = pages - 1.
        int pages = numTotalHits / docsPerPage; //full pages : [0, maxPage +1], Has value 0 only when there are no results at all!
        int restDocs = numTotalHits % docsPerPage; //docs of "last page" may not be enough for a whole page : [0 , docsPerPage)
        if (restDocs != 0) { // an extra not full page exist (the last page)
            pages++;
        }
        // Log printing
        if (debugMode) {
            ArrayList<String> keywords = queryToKeywords(queryString, specialCharacters);
            System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > [queryString: \"" + queryString + "\" number of keywords " + keywords.size() + ", total matching documents: " + numTotalHits + " (" + pages + " pages), time: " + timeMS + " MS]");
        }
        //Paging for results presentation
        int hitsStart = 0;
        int lastHitRequired = docsPerPage;
        //In case of wrong page, use default page
        if (pages > 0) { // at least one page exists (there are some examples)
            if (page >= pages) {
                // if page is too big, use last page
                page = pages - 1;
            } else if (page < 0) {

                //if page is too small. use first page
                page = 0;
            } // else page is already valid

        } else { // there are no results
            // Log printing
            if (debugMode) {
                System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > [queryString: \"" + queryString + "\", total matching documents: 0]");
            }
            //send empty JSON
            return json = gson.toJson(null);
        }
        System.out.println("Ordered into pages");


// calculate index of first document of the page
        hitsStart = page * docsPerPage;
        lastHitRequired = hitsStart; //to begin with
        if (restDocs != 0 && (pages - page) == 1) {//this is the last page and is not full of docs
            lastHitRequired += restDocs;
        } else { // it is a normal page full of docs
            lastHitRequired += docsPerPage;
        }

        int hitsEnd = Math.min(lastHitRequired, hitsStart + docsPerPage);

        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(query, hitsEnd);
        ScoreDoc[] hits = docs.scoreDocs;
        // 4. display results
        System.out.println("Found " + hits.length + " hits.");
        //System.out.println(docs.scoreDocs.toString());

        System.out.println("Hits start: " + hitsStart + "\tHits end: " + hitsEnd);
        //JSONArray JSONdocs = new JSONArray();\
        Citation citation=null;
        //int i;
        for (int i = 0; i <= page; i++) {
            if (i == page) {
                citation = new Citation(docsPerPage, i, numTotalHits);
                for (int j = i * docsPerPage; j < i * docsPerPage + docsPerPage; j++) {
                    Document doc = getSearcher().doc(hits[j].doc);
                    if (doc != null) {
                        //JSONdocs.add(getDocumentJSON(doc, (float)hits[i].score, queryString));
                        try {
                            //System.out.println(Arrays.toString(doc.getValues("title")));
                            citation.addDocument(doc.get("pubmed_id"), doc.get("title"), doc.get("abstract"));
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    } else {
                        // Log printing
                        System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > Warning! [Empty document with index : " + i + " ]");
                    }
                }
            }

        }



        /*for (ScoreDoc hit : hits) {
            int docId = hit.doc;
            Document d = searcher.doc(docId);
            //System.out.println((i + 1) + ". " + d.get("pubmed_id") + "\t" + d.get("abstract"));
            citations.addDocument(d.get("pubmed_id"), d.get("abstract"));
        }*/



        json=gson.toJson(citation);
        System.out.println(json);
        /*stopwatch.stop();
        System.out.println("Runtime: " + stopwatch.getTime());*/
        // reader can only be closed when there
        // is no need to access the documents any more.



        reader.close();
        //index.close();
        return json;
    }

    /*public String search() throws IOException, ParseException {
        /*if (args.length<2) {
            System.out.println("Invalid arguments!");
            return;
        }
        // 0. Specify the analyzer for tokenizing text.
        //    The same analyzer should be used for indexing and searching

    }*/

    private static void addDoc(IndexWriter w, String file) throws IOException {
        System.out.println("Entered method");
        CSVManager manager=new CSVManager(file);
        List<String[]> results=manager.Read();
        System.out.println("Read .csv");
        results.remove(0);
        Document doc;
        for (String[] row:results){
            doc = new Document();
            doc.add(new TextField("title", row[3], Field.Store.YES));
            doc.add(new TextField("abstract", row[8], Field.Store.YES));
            doc.add(new StringField("pubmed_id", row[6], Field.Store.YES));
            w.addDocument(doc);
        }


        //doc.add(new TextField("title", title, Field.Store.YES));
        // use a string field for isbn because we don't want it tokenized
        //doc.add(new StringField("isbn", isbn, Field.Store.YES));
    }



    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExampleJson() {
        return null;
    }

    @Override
    public String searchId(String id) throws Exception {
        return null;
        /*id = this.cleanQuery(id, specialCharacters).trim();
        Query query = this.getIdParser().parse(id);

        //Search and calculate the search time in MS
        Date startDate = new Date();
        // Collect 1 top Doc - only one concept should correspond to an ID
        TopDocs results = this.getIdSearcher().search(query, 1);
        Date endDate = new Date();
        Long timeMS = endDate.getTime()-startDate.getTime();

        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;

        // Log printing
        if(debugMode) {
            System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > [queryString: \"" + id + "\" (concept id), total matching documents: " + numTotalHits + " , time: " + timeMS + " MS]");
        }

        if(numTotalHits < 1) {// there are no results
            //send empty JSON
            ResponseJSON = getResultJSON(new JSONArray(), 1, id, 0, numTotalHits, timeMS);
        } else { // concept found
            //Prepare JSON Response String
                JSONArray JSONdocs = new JSONArray();
                //fetch the only concept from hits table
                int i = 0; // the index of the concept, only one concept should be contained here
                    Document doc = getSearcher().doc(hits[i].doc);
                    if (doc != null) {
                        JSONdocs.add(getDocumentJSON(doc, (float)hits[i].score, ""));
                    } else {
                        // Log printing
                        System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > Warning! [Empty document for concept with id : " + id + " ]");
                    }

            ResponseJSON = getResultJSON(JSONdocs, 1, id, 0, hits.length, timeMS);
        }
        return ResponseJSON;*/

    }

    public MultiFieldQueryParser getParser() {
        return parser;
    }

    public void setParser(MultiFieldQueryParser parser) {
        this.parser = parser;
    }

    public static int getHitsMax() {
        return hitsMax;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }
    public void setSearcher(IndexSearcher searcher){this.searcher=searcher;}

    public IndexReader getReader() {
        return reader;
    }
    public void setReader(IndexReader reader) {
        this.reader = reader;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }
    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public String getDefaultNamespace() throws Exception {
        return null;
    }

    private String getIndexPath() {
        return indexPath;
    }
    private void setIndexPath(String indexPath) {
        this.indexPath=indexPath;
    }
}
