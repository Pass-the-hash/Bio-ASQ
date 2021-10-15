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
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//add lucene-core-5.3.1.jar
import org.apache.lucene.document.Document;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
//add lucene-analyzers-common-5.3.1.jar
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.CachingWrapperQuery;
//lucene-queryparser-5.3.1.jar
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.MMapDirectory;
//add json-simple-1.1.1.jar
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


/**
 *
 * @author tasosnent
 */
public class SearchDocuments extends Searcher{

    //Hardecoded values
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static final String maxDate = "2017/11/27"; //freezing date (i.e. max DateCreated in Annual Medline Baseline Repository) - actually not used, date restriction implied by using MBR2017. This is kept for backwards compatibility, just to have a date to include in the response JSON sent to participants.
    private static final String minDate = "0001/01/01"; //absolute minimum date
    // full restriction not used by service at the moment e.g. 2016/01/07 : "(transcription factor (0001\\/01\\/01[Date - Create] :2013\\/12\\/13[Date - Create])  hasabstract )"
//    private static final String stdRestrictions = " ownernlm hasabstract not hascommenton not hasretractionof not haserratumfor not haspartialretractionof not hasrepublishedin not hasupdatein not Editorial[PT] not Comment[PT] not Letter[PT] not News[PT] not Review[PT] not pubmednotmedline[sb] not indatareview[sb]";
    private static final String stdRestrictions = " hasabstract";
    private static final int hitsMax = 100000; // "search window" search for hitsMax top documents each time.
    private static final int totalHitsMax = 100000; // maximum of hits to take into account as results.
    private static final int maxNumberOfCachedQueries = 250;
    private static final long maxRamBytesUsed = 1000 * 1024L * 1024L; // 100MB
    private int minIndexSize = 1000; // [QueryCachingPolicy.CacheOnLargeSegments] only caches on a given segment if the total number of documents in the index is greater than minIndexSize
    private float minSizeRatio = 0.001F; // [QueryCachingPolicy.CacheOnLargeSegments] number of documents in the segment divided by the total number of documents in the index is greater than or equal to minSizeRatio
    private static HashMap <String, String> supportedIndexFields = new <String, String> HashMap(); //Supported index Fields and their pubmed counterpart ("Title","ArticleTitle")

    private String indexPath = null; 
    private IndexReader reader = null;
    private IndexSearcher searcher = null;
    private Analyzer analyzer = null;
    private MultiFieldQueryParser parser = null;
    private Sort sort = null;
    private static LRUQueryCache queryCache;
    private static QueryCachingPolicy defaultCachingPolicy;
    
    //Custom caching
    private static ArrayList <String> customCachedQueries = new ArrayList <String> ();    
    private static HashMap <String, TopDocs> customCachedResults = new HashMap <String, TopDocs>();    
    private static HashMap <String, Long> customCachedTimeMS = new HashMap <String, Long>();    
    private static int maxCustomCachedQueries = 3;
    
    private String name = "Document Searcher"; // a name for the searcher - used for log printing 
    private String exampleJson = "Example : json={\"findPubMedCitations\": [\"search terms\", 1, 5]}"; // an example of a valid search json object provided during search 

    /**
     * Constructor
     * @param indexPath
     * @throws IOException 
     */
    public SearchDocuments(String indexPath) throws IOException{
        this.setIndexPath(indexPath); 
        
        //TO DO : add config.properties file
        //read configuration file and update static variables adequately
//        readConf();

        // Supported index Fields and their pubmed counterpart ("Title","ArticleTitle")       
        supportedIndexFields.put("Title", "ArticleTitle");
        supportedIndexFields.put("TI", "ArticleTitle");
        supportedIndexFields.put("Abstract", "AbstractText");
        supportedIndexFields.put("AB", "AbstractText");
        supportedIndexFields.put("PMID", "PMID");
        supportedIndexFields.put("UID", "PMID");
        //TO DO : add all supported index fields
        //TO DO : special words (i.e. nothascommenton etc)   
        
        // Lucene objects
        
        /* Sorting */
        // Fields used for reverse chronological sorting of results
        // This Fields are indexed with no tokenization (for each element a StringField AND a SortedDocValuesField are added)
            // Using SortField.Type.STRING is valid (as an exception) beacause years are 4-digit numbers resulting in identical String-sorting and number-sorting.
        SortField sortFieldYear = new SortField("PubDate-Year", SortField.Type.STRING, true);
        
        //TO DO : make custom comparators for the rest fields where lexilogical comparison is not valid
            // OR, store Pud Date as Date and use date comparison
            // Idealy one "complex-field" should be used for sorting, taking into account Year, Month, Day, Season and MedlineDate together)
//        SortField sortFieldMonth = new SortField("PubDate-Month",SortField.Type.STRING,true);
//        SortField sortFieldDay = new SortField("PubDate-Day",SortField.Type.STRING,true);
//        SortField sortFieldSeason = new SortField("PubDate-Season",SortField.Type.STRING,true);
//        SortField sortFieldMedlineDate = new SortField("PubDate-MedlineDate",SortField.Type.STRING,true);
//        this.setSort(new Sort(sortFieldYear, sortFieldMonth, sortFieldDay, sortFieldSeason, sortFieldMedlineDate));
        this.setSort(new Sort(sortFieldYear));
        
        /* Reading the index */
//        this.setReader(DirectoryReader.open(FSDirectory.open(Paths.get(getIndexPath()))));
        this.setReader(DirectoryReader.open(MMapDirectory.open(Paths.get(getIndexPath()))));
        this.setSearcher(new IndexSearcher(getReader()));
        this.setAnalyzer(new StandardAnalyzer());
        
        /* Caching */
        // these cache and policy instances can be shared across several queries and readers
        // it is fine to eg. store them into static variables
        // TO DO: Use stored (old) expert-queries (from mongoDB) and MESH terms to "prepare" cache.
        queryCache = new LRUQueryCache(maxNumberOfCachedQueries, maxRamBytesUsed);
//        defaultCachingPolicy = new UsageTrackingQueryCachingPolicy();
        defaultCachingPolicy = new QueryCachingPolicy.CacheOnLargeSegments(minIndexSize, minSizeRatio);
        this.getSearcher().setQueryCache(queryCache);
        this.getSearcher().setQueryCachingPolicy(defaultCachingPolicy);
        
        /*  All fields as default for search */
        Iterator <String> allFieldsIterator = org.apache.lucene.index.MultiFields.getFields(reader).iterator();
        ArrayList <String> a = new ArrayList();
        String current = "";
        while(allFieldsIterator.hasNext()){
            current = allFieldsIterator.next();
            if(!current.startsWith("PubDate-")){
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
    //Used just for testiong search method
//    public static void main(String[] args) {
//    //Request JOSN data
//        //Hardcoded Request JSON Object String - Just for testing
//        try{
//            //test code
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"((\\\"transcription factor\\\" adult)[Abstract] \\\"heart disease\\\" male[Title]) NOT (foxp3 asthma)\", 12, 11]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"(\\\"transcription factor\\\" [Title] \\\"heart disease\\\" male[Title]) OR (foxp3 asthma)\", -1, 11]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"\\\"transcription factor\\\" OR adult\", 1, 11]}");
//            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"Aicardi-Goutieres syndrome\", 1, 11]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"factor\", 0, 20]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findPubMedCitations\": [\"25861244[UID]\", 1, 10]}");
//        
//            JSONArray request = (JSONArray) parsedRequest.get("findPubMedCitations");
//
//            String query = (String) request.get(0);
//            long page = (long) request.get(1);
//            long articlesPerPage = (long) request.get(2);
//          
//        //Search in given index
//            //Hardcoded  indexpath - just for testing
////            Commented for Server compile                                                   
//            String indexPath = "D:\\Frozen index Used 20160113\\Documents_index";
////            String indexPath = "D:\\Documents_index_subset2";
////            String indexPath = "/home/bioasqer/projects/Documents_index";
//            SearchDocuments searcher = new SearchDocuments(indexPath);
//            try{
//                System.out.println(">Result JSON : " + searcher.search(query, (int) page, (int)articlesPerPage).replace("{", "\n{"));
//                //test code
//                for(int i=0; i<10;i++)
//                searcher.search(query, (int) page, (int)articlesPerPage);
//            } catch (Exception e) {
//                System.out.println(" caught a (searcher.Search) " + e.getClass() + "\n with message: " + e.getMessage());
//            }
//        } catch (Exception e) {System.out.println("Exception in JSON decoding : " + e.getMessage());}
//    }
        
    /**
     *  search queryString in all default Fields
     * @param queryString
     * @param page
     * @param articlesPerPage
     * @return
     * @throws Exception 
     */
    public String search( String queryString, int page, int articlesPerPage) throws Exception {    
        String ResponseJSON = null;  
        TopDocs results = null;
        Long timeMS = 0L;
        boolean foundInCustomCache = false;//variable just for logging
        //Check if query is in custom cache
        if(customCachedQueries.contains(queryString)){ // this query is in custom cache
            foundInCustomCache = true;
            // Log printing
            if(debugMode) {
                System.out.println(" " + new Date().toString() + " Search Documents > Query found in Custom Cache: " + queryString );
            }
            int customCacheIndex = customCachedQueries.indexOf(queryString);
            results = customCachedResults.get(queryString);
            timeMS = customCachedTimeMS.get(queryString);    
        } else {// it's a new query
            //Transform and restrict query from pubmed form to lucene form
            String LuceneQuery = PubmedQueryToLucene(queryString);

            LuceneQuery = restrictQuery(LuceneQuery);       

            Query query = getParser().parse(LuceneQuery);
            //chache query
            query = query.rewrite(getReader());
            Query cacheQuery = queryCache.doCache(query.createWeight(getSearcher(), true), defaultCachingPolicy).getQuery();

            CachingWrapperQuery wrappedQuery = new CachingWrapperQuery(cacheQuery);
 
            //Search and calculate the search time in MS
            Date startDate = new Date();
            //Collect hitsMax top Docs sorting by reverse chronological ranking (only year taken into acount so far)
            results = getSearcher().search(wrappedQuery, getHitsMax(), getSort(), true, false);
            Date endDate = new Date();
            timeMS = endDate.getTime()-startDate.getTime();
    
//    Do custom chachig here...
        //int customCacheIndex = customCachedQueries.indexOf(queryString);
        //results = customCachedResults.get(queryString);
        //timeMS = customCachedTimeMS.get(queryString);  
        
            // Ensure that max queries not exceeded
            if(customCachedQueries.size() >= maxCustomCachedQueries) {
                //remove first element
                String removedQuery = customCachedQueries.remove(0);
                customCachedResults.remove(removedQuery);
                customCachedTimeMS.remove(removedQuery);
            } 
            //update cache
            if(customCachedQueries.size() < maxCustomCachedQueries) {
                customCachedQueries.add(queryString);
                customCachedResults.put(queryString, results);
                customCachedTimeMS.put(queryString, timeMS);
            }
        }
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        //page : [0, maxPage], maxPage = pages - 1.
        int pages = numTotalHits/articlesPerPage; //full pages : [0, maxPage +1], Has value 0 only when there are no results at all!
        int restDocs = numTotalHits%articlesPerPage; //docs of "last page" may not be enough for a whole page : [0 , articlesPerPage)
        if(restDocs != 0){ // an extra not full page exist (the last page)
            pages++; 
        }

            // Log printing
            if(debugMode & !foundInCustomCache) {
//                System.out.println(" " + new Date().toString() + " Search Documents > [queryString: \"" + queryString + "\", total matching documents: " + numTotalHits + " (" + pages + " pages), time: " + timeMS + " MS]" + " > [cacheQuery.ramBytesUsed(): " + queryCache.ramBytesUsed()+"]");
                System.out.println(" " + new Date().toString() + " Search Documents > [queryString: \"" + queryString + "\", total matching documents: " + numTotalHits + " (" + pages + " pages), time: " + timeMS + " MS]" );
            }
        
        //Paging for results presentation
        int hitsStart = 0;
        int lastHitRequired = articlesPerPage;            
        
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
            //send empty JSON
            return getResultJSON(new JSONArray(), articlesPerPage, queryString, page, numTotalHits, timeMS);
        }

        // calculate index of first document of the page
        hitsStart = page * articlesPerPage; 
        lastHitRequired = hitsStart; //to begin with 
        if(restDocs !=0 &(pages-page) == 1){//this is the last page and is not full of docs
            lastHitRequired += restDocs;
        } else { // it is a normal page full of docs
            lastHitRequired += articlesPerPage;
        }

        int hitsEnd = Math.min(lastHitRequired, hitsStart + articlesPerPage);

        //Prepare JSON Response String
            //documents to be returned
            JSONArray JSONdocs = new JSONArray();
            for (int i = hitsStart ; i < hitsEnd; i++) {
                Document doc = getSearcher().doc(hits[i].doc);
                if (doc != null) {
                    JSONdocs.add(getDocumentJSON(doc,(float)hits[i].score));
                } else {
                    // Log printing
                    System.out.println(" " + new Date().toString() + " Search Documents > Warning! [Empty document with index : " + i + " ]");
                }
            }
      
        ResponseJSON = getResultJSON(JSONdocs, articlesPerPage, queryString, page, hits.length, timeMS);
        return ResponseJSON;
        
     }
 
    /**
     * Convert given pubmed query to lucene syntax (step three)
     *      handle field names and brackets
     * @param stringPart
     * @return 
     */
    private static String handleNonPhraseToken(String stringPart) {
        // Set of final terms to be used (no empty elements, right syntax of index fields)
        ArrayList <String> terms = new ArrayList <String> ();
        
        /**  Case Index field **/
        if(stringPart.contains("[") | stringPart.contains("[")){ // at least an Index field contained here
            //separate brackets for term with one space (extra spaces dont matter, trim() is used later)
            stringPart = stringPart.replace("[", " [");
            stringPart = stringPart.replace("]", "] ");
        }

        /**  Case Bag of terms **/
        //replace space with AND (change default operator to match pubMed's)
                // No need for this if setDefaultOperator(QueryParser.Operator.AND) is used
        
        String[] tokens = stringPart.split("\\s");
        if(tokens.length > 0){ 
            for(int i = 1 ; i < tokens.length ; i++){
                tokens[i] = tokens[i].trim();
                /* Case index field [fieldName] */
                if(tokens[i].startsWith("[") & tokens[i].endsWith("]")){
                    tokens[i] = tokens[i].replace("[","");// clean field name from brackets
                    tokens[i] = tokens[i].replace("]","");// clean field name from brackets
                    //Add index field as prefix to previus (not empty) term (as opening index fields handled separately, this index field should not be the first term of string part)
                    int tmp = i-1;
                    boolean perviusTermFound = false;
                    while(!perviusTermFound & tmp >= 0){
                        if(!tokens[tmp].equals("")){
                            perviusTermFound = true;
                            //TO DO : handle wrong syntax (e.g. ... AND [ArticleTitle])
                            String luceneField = supportedIndexFields.get(tokens[i]);
                            if(luceneField != null){
                                tokens[tmp] = luceneField + ":" + tokens[tmp];
                            } // else : Lucene counterpart not found, unsupported field - do nothing
                        } else {
                            tmp--;
                        }
                    }
                    //Empty field to not be added as a search term in query
                    tokens[i] = "";
                } 
                
                /*
                
                else if(!tokens[i].equals("AND") & !tokens[i].equals("")) {
                    terms.add(tokens[i]);
                }
                */
            }
        }
        
        String luceneQueryPart = "";
        boolean notWaiting = false;
        boolean orWaiting = false;
        for(int i = 0 ; i < tokens.length ; i++){

            if(!tokens[i].equals("AND") & !tokens[i].equals("")){
            //in case of AND nothing should happen, it is the default operator
            //empty elements are also ignored as useless
                
                if(!tokens[i].equals("NOT") & !tokens[i].equals("OR") ){ // it's not an operator, it's a term
                    
                    if(!luceneQueryPart.endsWith(" OR ") & !luceneQueryPart.endsWith(" -") ) { 
                        luceneQueryPart += " +"; 
                    } 
                        luceneQueryPart += tokens[i];
                } else {
                    if(tokens[i].equals("NOT")) {
                        luceneQueryPart += " -";
                    }
                    if(tokens[i].equals("OR")){
                        luceneQueryPart += " OR ";
                    }
                
                }    
            } 
        }
        
        return luceneQueryPart;
    }
    
    /**
     * Create query part for custom date restriction support
     * 
     * @param startDate
     * @param endDate
     * @return 
     */
    private static String dateRangeRestriction(String startDate, String endDate){
        String restriction = "";
        String[] startDateParts = startDate.split("/");
        String[] endDateParts = endDate.split("/");
        int prevYear = Integer.parseInt(endDateParts[0]) - 1; //year previus to End year
        int nextYear = Integer.parseInt(startDateParts[0]) + 1; //year next to End year
        if(prevYear-nextYear > 1){ // For intermediate years we may not care about days and months
            restriction += " PubDate-Year:[" + nextYear + " TO " + prevYear + "] OR ";
        }
        if(endDateParts[0].equals(startDateParts[0])){ // Year of start and end is the same
             restriction += "( +PubDate-Year:" + endDateParts[0] 
                        + "  +PubDate-Month:[" + startDateParts[1] + " TO " + endDateParts[1] 
                        + "] +PubDate-Day:[" + startDateParts[2] + " TO " + endDateParts[2] + "])";
        } else {
            restriction += "( +PubDate-Year:" + endDateParts[0] 
                        + "  +PubDate-Month:[ 01 TO " + endDateParts[1] 
                        + "] +PubDate-Day:[ 01 TO " + endDateParts[2] + "]) OR "
                        + "( +PubDate-Year:" + startDateParts[0] 
                        + "  +PubDate-Month:[ " + startDateParts[1] + " TO 12"  
                        + "] +PubDate-Day:[ " + startDateParts[2] + " TO 31 ])";
        }
        restriction = "(" + restriction + ")";
        return restriction;
    }
    
    /**
     *  Convert given pubmed query to lucene syntax (step two) 
     *      handle quotes
     * @param PubmedQuery
     * @return 
     */
    private static String handlePhrases(String PubmedQuery) {
        //Find phrases "between double quotes"
        String[] phrases = PubmedQuery.split("\"");
        for(int i = 0 ; i < phrases.length ; i++){
            phrases[i] = phrases[i].trim();
            if(!phrases[i].equals("")){ // String not Empty
                if(i%2 != 0){ // String surrounded by quotes, i.e. a phrase
                    phrases[i] = "\"" + phrases[i] + "\"";
                } else { // not a phrase, i.e. a bag of terms, operator or [field of index] (inside brackets)
                    if(phrases[i].startsWith("[")){ //index field of previus component contained (i.e. case: "..."[...])
                        boolean perviusPhraseFound = false; // True if index field was added to previus phrase element
                        // Get index field of previus component and handle proprietly
                        String [] tokens = phrases[i].split("]");
                        String indexField = tokens[0].replace("[", "");
                        String luceneField = supportedIndexFields.get(indexField);
                        if(luceneField != null){  
                            //add "indexField:" as a prefix in previus (not empty) phrase
                            int tmp = i-1;
                            while(!perviusPhraseFound & tmp >= 0){
                                if(!phrases[tmp].equals("")){
                                    perviusPhraseFound = true;
                                    //TO DO : handle wrong syntax (e.g. ... AND [ArticleTitle])
                                        phrases[tmp] = luceneField + ":" + phrases[tmp] + "";
                                } else {
                                    tmp--;
                                }
                            }
                        } // else : Lucene counterpart not found, unsupported field - do nothing
                        // Remove from current phrase this index field (prefix)
                        phrases[i] = phrases[i].substring(indexField.length()+2 );
                    } 
                    // Further handling...
                    phrases[i] = handleNonPhraseToken(phrases[i]);
                }
            } //else {  /* Empty token, do nothing with this */ System.out.println("\t\t\t empty"); }
        }
        
        String luceneQuery = "";
        boolean fisrtPhrase = true;
        for(int i = 0 ; i < phrases.length ; i ++){
            if(phrases[i].length() > 0){
                if(!phrases[i].startsWith(" OR ") & !phrases[i].startsWith(" -") & !phrases[i].startsWith(" +") ){
                    if(fisrtPhrase){
                        luceneQuery += " +";
                        fisrtPhrase = false;
                    } else {
                        if(!phrases[i-1].endsWith(" OR ") & !phrases[i-1].endsWith(" -") & !phrases[i-1].endsWith(" +")){
                            luceneQuery += " +";
                        }
                    }                
                } // add default operator + when required (no OR or - is present for that term)
                luceneQuery += phrases[i];
            }// else empty phrase : skip
        }
        return luceneQuery;
    }
    
    /**
     * Convert given pubmed query to lucene syntax (step one) 
     *      handle special characters and words
     * @param PubmedQuery
     * @return 
     */
    private static String PubmedQueryToLucene(String PubmedQuery) {
        String luceneQuery = "";
        //remove hasabstract components
        PubmedQuery = PubmedQuery.replaceAll("(NOT|AND|OR) hasabstract\\[text\\]", "");
        PubmedQuery = PubmedQuery.replaceAll("hasabstract\\[text\\] (NOT|AND|OR)", "");
        PubmedQuery = PubmedQuery.replaceAll("hasabstract\\[text\\]", "");
        PubmedQuery = PubmedQuery.replaceAll("(NOT|AND|OR) hasabstract", "");
        PubmedQuery = PubmedQuery.replaceAll("hasabstract (NOT|AND|OR)", "");
        PubmedQuery = PubmedQuery.replaceAll("hasabstract", "");

        //replace date component
        String dateRangeRestriction = "";
        // e.g. (\"0001/01/01\"[PDAT] : \"2013/03/14\"[PDAT])
        String dateRange = "\\(\"(\\d+/\\d+/\\d+)\"\\[PDAT\\]\\s*:\\s*\"(\\d+/\\d+/\\d+)\"\\[PDAT\\]\\)";
        Pattern dateRangePattern =  Pattern.compile(dateRange);
        Matcher matcher = dateRangePattern.matcher(PubmedQuery);
        if(matcher.find()){
            String startDate = matcher.group(1);
            String endDate = matcher.group(2);
//            System.out.println(" > " + startDate + " " + endDate );
            dateRangeRestriction = dateRangeRestriction(startDate, endDate);
            PubmedQuery = PubmedQuery.replaceAll(dateRange, "CustomDateRangeRestriction");
        }

        
//        System.out.println(">>>" + PubmedQuery );
        //Special Case, when query is just a number, consider it as a PMID
        // TO DO : add extra cases for more than one PMIDs too 
        if(PubmedQuery.trim().matches("\\d+")){
            luceneQuery = "PMID:" + PubmedQuery.trim();
        } else { //General case of query, not just a number        
            ArrayList <String> parts = new <String> ArrayList();
            //Find parts [between parentheses]
            if(PubmedQuery.contains("(") & PubmedQuery.contains(")"))//parentheses in the string
            {
                String tmpStr = "";
                String indexField ="";
                boolean insideIndexField = false;

                for(int i = 0; i < PubmedQuery.length() ; i++ ){
                    if(PubmedQuery.charAt(i) == '(' | PubmedQuery.charAt(i) == ')') { // new part start
                        tmpStr = tmpStr.trim();
                        if(!tmpStr.equals("")){
                            parts.add(handlePhrases(tmpStr));
                            tmpStr = "";
                        }
                        parts.add(" " + PubmedQuery.charAt(i) + " ");
                    } else if(PubmedQuery.charAt(i) == '[' & tmpStr.trim().equals("")) { // index field opening a part, refers to previus part (i.e. ( ... ...)[...] case)
                        //handle index field here because the previus part will be not available to handlePhrases
                        insideIndexField = true;
                    } else if(insideIndexField) { 
                        if(PubmedQuery.charAt(i) == ']'){// end of index field (opening a part)
                            tmpStr = tmpStr.trim();
                            if( !tmpStr.equals("")){ // add this index field to previus element
                                String luceneField = supportedIndexFields.get(tmpStr);
                                if(luceneField != null){ // field supported
                                    boolean previusPartFound = false;
                                    boolean previusIsParenthesis = false;
                                    int j = parts.size()-1;
                                    while(j >= 0 & !previusPartFound){ 
                                        String prevPart = parts.get(j).trim();
                                        if(prevPart.equals(")")){
                                            previusIsParenthesis = true;
                                        } else if(prevPart.equals("(") & previusIsParenthesis){ //beginig of previus paretheses reached
                                            String prevClause = "";
                                            for(int k = parts.size()-1; k >= j ; k--){
                                                prevClause = parts.get(k) + prevClause;
                                                parts.remove(k);
                                            }
                                            parts.add(luceneField + ":" + prevClause);
                                            previusPartFound = true;
                                        } else if(!previusIsParenthesis){ // not parentheses, it's a single term
                                            parts.remove(j);// remove part without index field
                                            parts.add( luceneField + ":" + prevPart);// add again with index field
                                            previusPartFound = true;
                                        }
                                        j--;
                                    }
                                }
                                tmpStr = "";
                            }
                            insideIndexField = false;
                        } else {
                             tmpStr += PubmedQuery.charAt(i);
                        }
                    } else { // continue existing part
                        tmpStr += PubmedQuery.charAt(i);
                    }
                }
                tmpStr = tmpStr.trim();
                if(!tmpStr.equals("")){
                    parts.add(handlePhrases(tmpStr));
                }
            } else { // no paretheses, do further handling
                luceneQuery = handlePhrases(PubmedQuery);        
            }

            //handle boolean operators
            boolean fisrtPhrase = true;
            for(int i = 0 ; i < parts.size() ; i ++){
                String currentPart = parts.get(i);
                    if(!currentPart.startsWith(" OR ") & !currentPart.startsWith(" -") & !currentPart.startsWith(" +") & !currentPart.equals(" ) ")){
                        if(fisrtPhrase){
                            luceneQuery += " +";
                            fisrtPhrase = false;
                        } else {
                            if(!parts.get(i-1).endsWith(" OR ") & !parts.get(i-1).endsWith(" -") & !parts.get(i-1).endsWith(" +")){
                                luceneQuery += " +";
                            }
                        }                
                    } // add default operator + when required (no OR or - is present for that term)
                    luceneQuery += parts.get(i);
            }
        }
        // replace "CustomDateRangeRestriction" with real restriction
//                System.out.println(" *>>>" + luceneQuery );
        if(luceneQuery.contains("CustomDateRangeRestriction")){
            luceneQuery = luceneQuery.replaceAll("CustomDateRangeRestriction", dateRangeRestriction);
        }
        // replace " + (" with " +("
        if(luceneQuery.contains(" + (")){
            luceneQuery = luceneQuery.replaceAll(" \\+ \\(",  " +(");
        }
        // replace " - (" with " -("
        if(luceneQuery.contains(" - (")){
            luceneQuery = luceneQuery.replaceAll(" \\- \\(",  " -(");
        }        
//        System.out.println(" *>>>" + luceneQuery );

        return luceneQuery;
    }
    
    /**
     * Add standard restrictions to query
     * @param query
     * @return 
     */
    private String restrictQuery(String query) {
        
        //Add standard restrictions
//        full restrictions not used!
//        String[] stdRestrictions = new String[16];
        String[] stdRestrictions = new String[2];
        //Date restrictions are INCLUSIVE!
        //No Date restriction needed, since we use Annual Medline Baseline
//            String[] maxDateParts = getMaxDate().split("/");
//            int prevYear = Integer.parseInt(maxDateParts[0]) - 1; //year previus to Max year
//        stdRestrictions[0]=" +(DateCreated-Year:[0001 TO " + prevYear
//                        + "] OR ( +DateCreated-Year:" + maxDateParts[0] 
//                        + "  +DateCreated-Month:[01 TO " + maxDateParts[1] 
//                        + "] +DateCreated-Day:[01 TO " + maxDateParts[2] 
//                        + "])) "; //Date
        stdRestrictions[1]=" +AbstractText:[\"\" TO *]";
        
        // full restriction not used by service at the moment e.g. 2016/01/07 : "(transcription factor (0001\\/01\\/01[Date - Create] :2013\\/12\\/13[Date - Create])  hasabstract )"
        /*
        stdRestrictions[2]=" +MedlineCitation_Owner:NLM";
        stdRestrictions[3]=" -CommentsCorrections_RefType:commenton";
        stdRestrictions[4]=" -CommentsCorrections_RefType:retractionof";
        stdRestrictions[5]=" -CommentsCorrections_RefType:erratumfor";
        stdRestrictions[6]=" -CommentsCorrections_RefType:partialretractionof";
        stdRestrictions[7]=" -CommentsCorrections_RefType:republishedin";
        stdRestrictions[8]=" -CommentsCorrections_RefType:updatein";
        stdRestrictions[9]=" -PublicationType:editorial";
        stdRestrictions[10]=" -PublicationType:Comment";
        stdRestrictions[11]=" -PublicationType:Letter";
        stdRestrictions[12]=" -PublicationType:News";
        stdRestrictions[13]=" -PublicationType:Review";
        stdRestrictions[14]=" -MedlineCitation_Status:PubMed"; //equivalent to "PubMed-not-MEDLINE" as no other allowed value contains keyword "pubmed"
        stdRestrictions[15]=" -MedlineCitation_Status:Review"; //equivalent to "in-data-review" as no other allowed value contains keyword "review"
        */
        query = "+(" + query + ") " + StringArrayToString(stdRestrictions);
        
        return query;
    }
    
    /**
     * Prepares JSON Response String with results
     *
     * @param JSONdocs
     * @param articlesPerPage
     * @param keywords
     * @param page
     * @param size
     * @param timeMS
     * @return 
     */
    private String getResultJSON(JSONArray JSONdocs, int articlesPerPage, String keywords, int page, int size, Long timeMS  ) {
        
        //Hardcoded query
        //Create fullPubmedQuery
        String dateRestriction = " (" + getMinDate() + "[Date - Create] : " + getMaxDate() + "[Date - Create])";
        String fullPubmedQuery = "(" + keywords + dateRestriction +getStdRestrictions() + ")";

        JSONObject responseObject = new JSONObject();
            JSONObject result = new JSONObject();
                result.put("articlesPerPage", articlesPerPage);
                result.put("documents", JSONdocs);
                result.put("fullPubmedQuery", fullPubmedQuery);
                result.put("keywords", keywords);
                result.put("maxDate", getMaxDate());
                result.put("page", page);
                result.put("size", size);
                result.put("timeMS", timeMS);
        responseObject.put("result", result);
        
        return responseObject.toString();
    }
    
    /**
     * Convert an index doc to JSON object
     * @param doc
     * @param score
     * @return 
     */
    private static JSONObject getDocumentJSON(Document doc, float score){
    // Build  JSON object

        //get fiels values
        String documentAbstract = StringArrayToString(doc.getValues("AbstractText")); // (?)/(*) 1 Synonym: OtherAbstract(*)/AbstractText(+) - List
        Boolean fulltextAvailable = false;
        String journal = doc.get("Title"); // (?) No synonyms
        String meshAnnotations = null;
        JSONArray MHList = StringArrayToJSONList(doc.getValues("DescriptorName"));//(?)/(+)/1 No synonyms - List
        String pmid = doc.get("PMID"); // 1 with 2 synonyms (DeleteCitation/PMID, CommentsCorrections/PMID) but MedlineCitation/PMID is always the first encountered
        String sections = null;
        String title = doc.get("ArticleTitle"); // 1 No synonyms
        String year = doc.get("PubDate-Year"); // ? with synonyms but MedlineDate 
        //TO DO : MedlineDate when year not present (find year in free text)                

        //add field values to JSON object
        JSONObject docJSON = new JSONObject();
        docJSON.put("documentAbstract", documentAbstract);
        docJSON.put("fulltextAvailable", fulltextAvailable);
        docJSON.put("journal", journal);
        docJSON.put("meshAnnotations", meshAnnotations);
        docJSON.put("meshHeading", MHList);
        docJSON.put("pmid", pmid);
        docJSON.put("sections", sections);
        docJSON.put("score", score);
        docJSON.put("title", title);
        docJSON.put("year", year);

    return docJSON;
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
     * @return the maxDate
     */
    public static String getMaxDate() {
        return maxDate;
    }
    
    /**
     * @return the stdRestrictions
     */
    public static String getStdRestrictions() {
        return stdRestrictions;
    }   
    
    /**
     * @return the minDate
     */
    public static String getMinDate() {
        return minDate;
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
     * @return the maxRamBytesUsed
     */
    public static long getMaxRamBytesUsed() {
        return maxRamBytesUsed;
    }    

    /**
     * @return the sort
     */
    public Sort getSort() {
        return sort;
    }

    /**
     * @param sort the sort to set
     */
    public void setSort(Sort sort) {
        this.sort = sort;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return the exampleJson
     */
    public String getExampleJson() {
        return exampleJson;
    }
}
