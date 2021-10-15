/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package searchService;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//add lucene-core-5.3.1.jar
import org.apache.lucene.document.Document;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
//add lucene-analyzers-common-5.3.1.jar
import org.apache.lucene.analysis.standard.StandardAnalyzer;
//lucene-queryparser-5.3.1.jar
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
//add json-simple-1.1.1.jar
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author tasosnent
 */
public class SearchOBO extends ConceptSearcher{

    //Hardecoded values
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static final int hitsMax = 100000; // "search window" search for hitsMax top documents each time.
    private static final int totalHitsMax = 100000; // maximum of hits to take into account as results.

    private static ArrayList<String> specialCharacters = new ArrayList<String>();
    private String indexPath = null; 
    private String defaultNamespace = null;
    private IndexReader reader = null;
    private IndexSearcher searcher = null;//Default Searcher : serches names and synonyms - used by findEntitiesPaged - Used by participants for TaskB phaseA and by Annotation Tool to retrieve concepts 
    private Analyzer analyzer = null;
    private MultiFieldQueryParser parser = null;

    private IndexSearcher idSearcher = null;// extra searcher to search for concepts by id (used by scripts that merge system answers and import them to assessment DB)
    private QueryParser idParser = null;
    private String name = "OBO searcher"; // a name for the searcher - used for log printing 
    private String exampleJson = "Example : json={\"findEntitiesPaged\": [\"search terms\", 0, 5]} ";// an example of a valid search json object provided during search 
//            + ", Exmpale2 : json={\\\"retrieveEntities\\\": [\\\"search concept URL\\\", 0, 5]} "; 
    
    /**
     * Constructor
     * @param indexPath
     * @param defaultNamespace
     * @throws IOException 
     */
    public SearchOBO(String indexPath, String defaultNamespace) throws IOException{
        this.setIndexPath(indexPath); 
        this.setDefaultNamespace(defaultNamespace);
        
        //special Strings to be removed from query
        // lucene special characters are + - && || ! ( ) { } [ ] ^ " ~ * ? : \
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

        //TO DO : add config.properties file
        //read configuration file and update static variables adequately
//        readConf();
        
        // Lucene objects
        /* Reading the index */
        this.setReader(DirectoryReader.open(FSDirectory.open(Paths.get(getIndexPath()))));
        this.setSearcher(new IndexSearcher(getReader()));
        this.setAnalyzer(new StandardAnalyzer());
        this.setSearchFields();

        // extra searcher to search for concepts by id (used by scripts that merge system answers and import them to assessment DB)
        this.setIdSearcher(new IndexSearcher(getReader()));
        this.setIdParser(new QueryParser( "id", getAnalyzer()));
    }
    
    /**
     * Sets name and synonym fields of index as default search fields
     * @throws IOException 
     */
    private void setSearchFields() throws IOException{
    /*  All fields as default for search */
        Iterator <String> allFieldsIterator = org.apache.lucene.index.MultiFields.getFields(reader).iterator();
        ArrayList <String> a = new ArrayList();
        String current = "";
        while(allFieldsIterator.hasNext()){
            current = allFieldsIterator.next();
            if(current.equals("name") | current.equals("synonym")){//There's not meaning to search in name space. it's the same for all docs
                a.add(current);
            }
        }
        String[] allFields = new String[a.size()];
        allFields = a.toArray(allFields); //All index fields to be used as default search fields
        this.setParser( new MultiFieldQueryParser( allFields, getAnalyzer()));    
    }

    /**
     * @param args the command line arguments
     */
////    Used just for testing search method
//    public static void main(String[] args) {
//    //Request JOSN data
//        //Hardcoded Request JSON Object String - Just for testing
//        try{
//            //test code
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"Clinical Protocols\", 0, 5]}");
//
//    //Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
//            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"Clinical Protocol male\", 0, 5]}");
//
//            JSONArray request = (JSONArray) parsedRequest.get("findEntitiesPaged");
//
//            String query = (String) request.get(0);
//            long page = (long) request.get(1);
//            long docsPerPage = (long) request.get(2);
//            
////                test print code
////            System.out.println("Request JSON : " + parsedRequest.toJSONString());
//
//        //Search in given index
//            //Hardcoded  indexpath - just for testing
////            Commented for Server compile                                                   
//            String indexPath = "D:\\indexes Used\\OBO indexes Used 20160225\\Jochem_index";
////            String indexPath = "D:\\indexes Used\\OBO indexes Used 20160225\\mesh2016_index";
//            String defNameSpace = "http://www.biosemantics.org/jochem#";
////            String indexPath = "/home/bioasqer/projects/Jochem_index";
//            SearchOBO searcher = new SearchOBO(indexPath, defNameSpace);
//            try{
////                System.out.println(">Result JSON : " + searcher.searchId("D015655").replace("{", "\n{"));
//                System.out.println(">Result JSON : " + searcher.search(query, (int) page, (int)docsPerPage).replace("{", "\n{"));
//                //test code
////                for(int i=0; i<50;i++)
////                searcher.search(query, (int) page, (int)docsPerPage);
//            } catch (Exception e) {
//                System.out.println(" caught a (searcher.Search) " + e.getClass() + "\n with message: " + e.getMessage());
//            }
//        } catch (Exception e) {System.out.println("Exception in JSON decoding : " + e.getMessage());}
//    }
//        
    
    /**
     * Search given query in defaults search fields
     * 
     * @param queryString
     * @param page
     * @param docsPerPage
     * @return          ResponseJSON object with results
     * @throws Exception 
     */
    public String search( String queryString, int page, int docsPerPage) throws Exception {    
        
        String ResponseJSON = null;       
        
        queryString = cleanQuery(queryString, specialCharacters);
      
        Query query = getParser().parse(queryString);  
        
        //Search and calculate the search time in MS
        Date startDate = new Date();
        // Collect hitsMax top Docs
        TopDocs results = getSearcher().search(query, getHitsMax());
        Date endDate = new Date();
        Long timeMS = endDate.getTime()-startDate.getTime();
                    
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        //page : [0, maxPage], maxPage = pages - 1.
        int pages = numTotalHits/docsPerPage; //full pages : [0, maxPage +1], Has value 0 only when there are no results at all!
        int restDocs = numTotalHits%docsPerPage; //docs of "last page" may not be enough for a whole page : [0 , docsPerPage)
        if(restDocs != 0){ // an extra not full page exist (the last page)
            pages++; 
        }

        // Log printing
        if(debugMode) {
                    ArrayList<String> keywords = queryToKeywords(queryString, specialCharacters);
            System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > [queryString: \"" + queryString + "\" number of keywords " + keywords.size() + ", total matching documents: " + numTotalHits + " (" + pages + " pages), time: " + timeMS + " MS]");
        }
        //Paging for results presentation
        int hitsStart = 0;
        int lastHitRequired = docsPerPage;            
        
        //In case of wrong page, use default page
        if(pages > 0){ // at least one page exists (there are some examples)
            if(page >= pages){
                // if page is too big, use last page
                page = pages - 1;
            } else if(page < 0){

                //if page is too small. use first page
                page = 0;
            } // else page is allready valid 

        } else { // there are no results
        // Log printing
            if(debugMode) {
                System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > [queryString: \"" + queryString + "\", total matching documents: 0]");
            }            
            //send empty JSON
            return getResultJSON(new JSONArray(), docsPerPage, queryString, page, numTotalHits, timeMS);
        }

        // calculate index of first document of the page
        hitsStart = page * docsPerPage; 
        lastHitRequired = hitsStart; //to begin with 
        if(restDocs !=0 &(pages-page) == 1){//this is the last page and is not full of docs
            lastHitRequired += restDocs;
        } else { // it is a normal page full of docs
            lastHitRequired += docsPerPage;
        }

        int hitsEnd = Math.min(lastHitRequired, hitsStart + docsPerPage);

        //Prepare JSON Response String
            //documents to be returned
            JSONArray JSONdocs = new JSONArray();
            for (int i = hitsStart ; i < hitsEnd; i++) {
                Document doc = getSearcher().doc(hits[i].doc);
                if (doc != null) {
                    JSONdocs.add(getDocumentJSON(doc, (float)hits[i].score, queryString));
                } else {
                    // Log printing
                    System.out.println(" " + new Date().toString() + " Search " + this.getName() + " > Warning! [Empty document with index : " + i + " ]");
                }
            }
 
        ResponseJSON = getResultJSON(JSONdocs, docsPerPage, queryString, page, hits.length, timeMS);
        return ResponseJSON;
        
     }
    
    /**
     *  Search in ID Field (used from Assessment import script)
     *      docPerPage = 1 by default
     *      page = 0 by default
     * 
     * @param id    concept id to search for
     * @return      ResponseJSON of result
     * @throws Exception 
     */
    public String searchId(String id) throws Exception {    
        
        String ResponseJSON = null;       
        id = this.cleanQuery(id, specialCharacters).trim();
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
        return ResponseJSON;
        
     }
    
    /**
     * Prepares JSON Response String with results
     * 
     * @param JSONdocs          Results in JSON format
     * @param conceptsPerPage       
     * @param keywords              
     * @param page                  
     * @param size
     * @param timeMS
     * @return                  responseObject as a String
     */
    private String getResultJSON(JSONArray JSONdocs, int conceptsPerPage, String keywords, int page, int size, Long timeMS  ) {

        JSONObject responseObject = new JSONObject();
            JSONObject result = new JSONObject();
                result.put("findings", JSONdocs);
                result.put("keywords", keywords);
                result.put("page", page);
                result.put("conceptsPerPage", conceptsPerPage);
                result.put("timeMS", timeMS);
            responseObject.put("result", result);
        
        return responseObject.toString();
    }
       
    /**
     * Create a JSONObject for a given doc from index
     *      In case of searching by concept id     
     *          1)  keywords variable is the empty string "" 
     *          2)  "ranges" is an empty array 
     * 
     * @param doc
     * @param score
     * @param keywords
     * @return      JSONObject representation of an index doc 
     */
    private JSONObject getDocumentJSON(Document doc,float score, String keywords){
    // Build  JSON object
        //get fiels values
        String label = doc.get("name");
        String termId = doc.get("id");
        String uri = doc.get("namespace");
        if(uri == null){
             uri = this.getDefaultNamespace();
        } 
        uri += termId;
        
        JSONArray synonyms = StringArrayToJSONList(doc.getValues("synonym"));
        synonyms.add(label);
            
        //add field values to JSON object
        JSONObject docJSON = new JSONObject();
        JSONObject conceptJSON = new JSONObject();
            conceptJSON.put("label", label);
            conceptJSON.put("termId", termId);
            conceptJSON.put("uri", uri);  
        docJSON.put("concept", conceptJSON);
        
//        add matchedLabel and ranges
        // in case of "id search" keywords variable is the empty string...        
        if(keywords.equals("")){
            // ...in this case add the empty array as ranges
            conceptJSON.put("ranges", new JSONArray());
        } else {
            addRanges(docJSON, synonyms, keywords);
        }
        docJSON.put("score", score);
        
        return docJSON;
    } 
    
    /**
     * Find best matching synonym and specific ranges
     * 
     * @param docJSON
     * @param labels
     * @param queryString
     * @return              best matching synonym 
     */
    private String addRanges(JSONObject docJSON, JSONArray labels, String queryString ){
        ArrayList<String> keywords = queryToKeywords(queryString, specialCharacters);
        ArrayList<Pattern> keywordPatterns = keywordsToPatterns(keywords);
        
        //label best matching to query
        String matchedLabel = null;
        int matchedLabelindex = 0;
        int maxHits = 0;
        // count of how many times any keyword found in this label
        int[] rangesCount = new int[labels.size()];
        Matcher matcher = null;    
        Pattern pattern = null;
        String label = null;
        //count how many times each keywords apears in a label
        for(int i = 0 ; i < keywords.size() ; i++){//for each keyword
            pattern = keywordPatterns.get(i);
            for(int j = 0 ; j < labels.size() ; j++){//for each label
                label = labels.get(j).toString().toLowerCase();
                //find hits
                matcher = pattern.matcher(label);
                if (matcher.find())
                {
                    rangesCount[j] += matcher.groupCount();
                } 
            }
            pattern = null;
            matcher = null;
        }
        //find best matching label based on number of hits with keywords
        for(int i = 0 ; i < rangesCount.length ; i++ ){
            // use >= because in case of equal counts we prefer the last added label, i.e. the main name added in line 299 "synonyms.add(label);"
            if(rangesCount[i] >= maxHits){
                maxHits = rangesCount[i];
                matchedLabelindex = i;
                matchedLabel = labels.get(matchedLabelindex).toString();
            }
        }
        
        //find ranges for best match
        JSONArray ranges = new JSONArray();
        JSONObject rangeJSON = new JSONObject();
        String tmpMatchedLabel = matchedLabel.toLowerCase();
        String tmpKeyWord = null;
        int start = 0;
        int end = 0;
        int repeats = 0;
        for(int i = 0 ; i < keywords.size() ; i++){//for each keyword
            tmpKeyWord = keywords.get(i);
            while(tmpMatchedLabel.contains(tmpKeyWord) & repeats <= maxHits){//for each range of current keyword
               //itteration done from last to first hit (range) so that start and end indexes remain valid for the inintial (not edited) matchedLabel
               start = tmpMatchedLabel.lastIndexOf(tmpKeyWord);
               end = start + tmpKeyWord.length();
               
                //add start and end to range
               rangeJSON.put("end", end);
               rangeJSON.put("begin", start);
               ranges.add(rangeJSON);
 
                //remove last range from label so that previus range will be the last
                tmpMatchedLabel = tmpMatchedLabel.substring(0, start);
                
                //nullify for further use
                rangeJSON = new JSONObject();
                //number of repeats in while loop
                // extra condition to guarranty that no infinite loop occurs in case something goes wrong with "tmpMatchedLabel.contains(tmpKeyWord)" 
                repeats++;
            }
           start = 0;
           end = 0;
           tmpKeyWord = null;
           tmpMatchedLabel = matchedLabel.toLowerCase();
        }
        docJSON.put("ranges", ranges);
        docJSON.put("matchedLabel", matchedLabel);

        return matchedLabel;                
    }
    
    /**
     * @return the indexPath
     */
    public String getIndexPath() {
        return indexPath;
    }

    /**
     * @param aIndexPath the indexPath to set
     */
    public void setIndexPath(String aIndexPath) {
        indexPath = aIndexPath;
    }

    /**
     * @return the hitsMax
     */
    public static int getHitsMax() {
        return hitsMax;
    }
    
    /**
     * @return the totalHitsMax
     */
    public static int getTotalHitsMax() {
        return totalHitsMax;
    }    

    /**
     * @return the reader
     */
    public IndexReader getReader() {
        return reader;
    }

    /**
     * @param reader the reader to set
     */
    public void setReader(IndexReader reader) {
        this.reader = reader;
    }

    /**
     * @return the searcher
     */
    public IndexSearcher getSearcher() {
        return searcher;
    }

    /**
     * @param searcher the searcher to set
     */
    public void setSearcher(IndexSearcher searcher) {
        this.searcher = searcher;
    }

    /**
     * @return the analyzer
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * @param analyzer the analyzer to set
     */
    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * @return the parser
     */
    public MultiFieldQueryParser getParser() {
        return parser;
    }

    /**
     * @param parser the parser to set
     */
    public void setParser(MultiFieldQueryParser parser) {
        this.parser = parser;
    }

    /**
     * @return the defaultNamespace
     */
    @Override
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    /**
     * @param defaultNamespace the defaultNamespace to set
     */
    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    /**
     * @return the idSearcher
     */
    public IndexSearcher getIdSearcher() {
        return idSearcher;
    }

    /**
     * @param idSearcher the idSearcher to set
     */
    public void setIdSearcher(IndexSearcher idSearcher) {
        this.idSearcher = idSearcher;
    }

    /**
     * @return the idParser
     */
    public QueryParser getIdParser() {
        return idParser;
    }

    /**
     * @param idParser the idParser to set
     */
    public void setIdParser(QueryParser idParser) {
        this.idParser = idParser;
    }

    /**
     * @return the name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the exampleJson
     */
    public String getExampleJson() {
        return exampleJson;
    }
    
}
