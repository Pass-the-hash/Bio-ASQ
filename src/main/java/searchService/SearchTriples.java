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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.apache.lucene.queryparser.classic.ParseException;
//add json-simple-1.1.1.jar

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author tasosnent
 * 
 * Backup Service - In case TI search Triples on available : http://www.gopubmed.org/web/bioasq/linkedlifedata2/triples
 *      Based on a "simple logic"
 *      1)  Find some triples searching with given query
 *      2)  Each Object and Subject in triples considered an "entity"
 *      3)  For each "entity" collect triples it appears as Object or Subject (from the same triples set) 
 *      4)  Step 3 is the "relations" field in json response for the "entity"
 * 
 */
public class SearchTriples extends Searcher{

    //Hardecoded values
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static final int TripleHitsMax = 300000; // maximum of hits to take into account as triples results in each index search (*).
    private static final int maxTriplesInRelations = 100; // maximum triples to be included in a relations Array of an entity
            
    private static ArrayList<String> specialCharacters = new ArrayList<String>(); // characters, not supported, to be removed from query.
    // (*) More than one indexes used because number of documents exceeds the limit of lucene
    // !ATTENTION! convention on index naming - each index is named as "indexPath" with suffix "_0", "_1" etc
    private String  indexPath = null;
    private ArrayList<IndexReader> readers = new ArrayList<>(); // Read all indexes (*)
    private ArrayList<IndexSearcher> searchers = new ArrayList<>(); // Search all indexes (*)
    private ArrayList<MultiFieldQueryParser> parsers = new ArrayList<>();
    private Analyzer analyzer = null;
    private static HashMap <String, String> supportedIndexFields = new <String, String> HashMap(); //Supported index Fields and their query counterpart (e.g. "subj","subject")

    private static ArrayList <String> labelPredicates = new ArrayList <> (); // predicates with "label" meaning.
    private String name = "Triple Searcher"; // a name for the searcher - used for log printing 
    private String exampleJson = "Example : json={\"findEntitiesPaged\": [\"search terms\", 1, 5]}"; // an example of a valid search json object provided during search 

    /**
     * Constructor, initializes basic variables.
     * 
     * @param indexPath
     * @param indexPartsNum     More than one indexes used because number of documents exceeds the limit of lucene
     *                          !ATTENTION! convention on index naming - each index is named as "indexPath" with suffix "_0", "_1" etc
     * @throws IOException 
     */
    public SearchTriples(String indexPath, int indexPartsNum) throws IOException{
        this.setIndexPath(indexPath);
        
//        Update valid label predicates
        labelPredicates.add("http://data.linkedct.org/resource/linkedct/acronym");
        labelPredicates.add("http://purl.uniprot.org/core/alias");
        labelPredicates.add("http://purl.uniprot.org/core/alternativeName");
        labelPredicates.add("http://purl.uniprot.org/core/commonName");
        labelPredicates.add("http://purl.uniprot.org/core/fullName");
        labelPredicates.add("http://purl.uniprot.org/core/name");
        labelPredicates.add("http://purl.uniprot.org/core/orfName");
        labelPredicates.add("http://purl.uniprot.org/core/otherName");
        labelPredicates.add("http://purl.uniprot.org/core/recommendedName");
        labelPredicates.add("http://purl.uniprot.org/core/scientificName");
        labelPredicates.add("http://purl.uniprot.org/core/shortName");
        labelPredicates.add("http://purl.uniprot.org/core/submittedName");
        labelPredicates.add("http://purl.uniprot.org/core/synonym");
        labelPredicates.add("http://www.w3.org/2000/01/rdf-schema#label");
        labelPredicates.add("http://www.w3.org/2004/02/skos/core#altLabel");
        labelPredicates.add("http://www.w3.org/2004/02/skos/core#broadSynonym");
        labelPredicates.add("http://www.w3.org/2004/02/skos/core#narrowSynonym");
        labelPredicates.add("http://www.w3.org/2004/02/skos/core#prefLabel");
        labelPredicates.add("http://www.w3.org/2004/02/skos/core#relatedSynonym");
        labelPredicates.add("http://www.w3.org/2008/05/skos-xl#altLabel");
        labelPredicates.add("http://www.w3.org/2008/05/skos-xl#literalForm");
        labelPredicates.add("http://www.w3.org/2008/05/skos-xl#prefLabel");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/diseasome/resource/diseasome/name");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/brandName");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/chemicalIupacName");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/geneName");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/genericName");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/interactionInsert");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/name");
        labelPredicates.add("http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugbank/pdbId");        
        
        //special characters or Strings to be removed from query
        // lucene special characters are + - && || ! ( ) { } [ ] ^ " ~ * ? : \
//        specialCharacters.add("\"");
        specialCharacters.add("(");
        specialCharacters.add(")");
//        specialCharacters.add("[");
//        specialCharacters.add("]");
//        specialCharacters.add("+");
//        specialCharacters.add("-");
//        specialCharacters.add("?");
//        specialCharacters.add("!");
//        specialCharacters.add("^");
//        specialCharacters.add("~");
//        specialCharacters.add("*");
//        specialCharacters.add(":");
//        specialCharacters.add("AND");
//        specialCharacters.add("OR");
//        specialCharacters.add("NOT");

// Supported index Fields and their query counterpart (e.g. "subj","subject")       
        supportedIndexFields.put("subj", "subject");
        supportedIndexFields.put("obj", "object");        
        supportedIndexFields.put("pred", "predicate");        
        
        // Lucene objects
        /* Reading the index */
        this.setAnalyzer(new StandardAnalyzer());
        for(int i = 0 ; i < indexPartsNum ; i++){
            this.setReader(DirectoryReader.open(FSDirectory.open(Paths.get(getIndexPath() + "_" + i))));
            this.setSearcher(new IndexSearcher(getReader(i)));
            this.setSearchFields(i);
        }
    }
    /**
     *  Sets the fields to search in a specified index
     * @param index
     * @throws IOException 
     */
    private void setSearchFields(int index) throws IOException{
    /*  All fields as default for search */
        Iterator <String> allFieldsIterator = org.apache.lucene.index.MultiFields.getFields(getReader(index)).iterator();
        ArrayList <String> a = new ArrayList();
        String current = "";
        while(allFieldsIterator.hasNext()){
            current = allFieldsIterator.next();
                a.add(current);
        }
        String[] allFields = new String[a.size()];
        allFields = a.toArray(allFields); //All index fields to be used as default search fields
        this.setParser( new MultiFieldQueryParser( allFields, getAnalyzer()));    
    }
    
    /**
     * Used just for testing search method
     * @param args the command line arguments
     */
//    public static void main(String[] args) {
//    //Request JOSN data
//        //Hardcoded Request JSON Object String - Just for testing
//        try{
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"http://purl.uniprot.org/keywords/237 [obj]  NOT ( dna[pred] http://purl.uniprot.org/keywords/222[subj] transcription) \\\" nucleus cyto \\\" \", 0, 30]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"http://data.linkedct.org/resource/intervention/27946 OR http://data.linkedct.org/resource/trials/NCT00673179[subj]\", 0, 30]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"splicing OR http://data.linkedct.org/resource/intervention/27946\", 0, 30]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"endosome[obj] OR lusosome[obj]\", 0, 30]}");
//            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\" duchenne\", 0, 30]}");
////            JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException("{\"findEntitiesPaged\": [\"dna splicing mechanism\", 0, 5]}");
//
//            JSONArray request = (JSONArray) parsedRequest.get("findEntitiesPaged");
//
//            String query = (String) request.get(0);
//            long page = (long) request.get(1);
//            long docsPerPage = (long) request.get(2);
//           
//        //Search in given index
//            //Hardcoded  indexpath - just for testing
////            Commented for Server compile      
//            // !ATTENTION! convension on intex naming - each part ends with "_0", "_1" etc
//            String indexPath = "D:\\indexes Used\\Triples index\\Triples_index_20160318";
//            int indexPartsNum = 2;
////            String indexPath = "/home/bioasqer/projects/Jochem_index";
//            SearchTriples searcher = new SearchTriples(indexPath,indexPartsNum);
//            try{
//                System.out.println(">Result JSON : " + searcher.search(query, (int) page, (int)docsPerPage).replace("{", "\n{"));
////                System.out.println(">Result JSON : " + searcher.search(query, (int) page, (int)docsPerPage));
//
//            } catch (Exception e) {
//                System.out.println(" caught a (searcher.Search) " + e.getClass() + "\n with message: " + e.getMessage() );
//                e.printStackTrace();
//            }
//        } catch (Exception e) {System.out.println("Exception in JSON decoding : " + e.getMessage());}
//    }
     
    /**
     *  Search in all triple indexes (used more than one, due to size of data)
     * @param keywords
     * @param page
     * @param entitiesPerPage
     * @return
     * @throws Exception 
     */
    @Override
    public String search( String keywords, int page, int entitiesPerPage) throws Exception {    

        JSONArray entitiesResultArray = new JSONArray();
        JSONArray entitiesFullArray = new JSONArray();
        
        // Remove special chars and convert query to lucene format
        String keywordsString = queryToLucene(keywords);

        Date startDate = new Date();

        // Create Entities
        // those HashMaps must have the same keys, i.e. the entity string.
        HashMap <String, JSONArray> entities = new HashMap <>(); // Entities found in Triples retrieved
        HashMap <String, Float> entitiesScores = new HashMap <>(); // Scores of Entities: Maximum score of triples in each entities "reslations" set
        JSONArray triples = new JSONArray(); // All triples retrieved from the specific search.
        
        // Search in indexes for "keywordsString"  
        // Return triples retieved.
        // Initialize entities and entitiesScores Sets with Keys (entities) found in triples.
        triples.addAll( findEntities(entities, entitiesScores, keywordsString));
        
        // find triples (relations field in json response) for each entity 
        // update entities and entitiesScores Sets.
        loadEntities(entities, entitiesScores, triples);

        // Print a log message
        if(debugMode){
            Date endDate = new Date();
            Long timeMS = endDate.getTime()-startDate.getTime();
            System.out.println(" " + endDate.toString() + " Search triples in " + this.readers.size() + " indexes >  total matching entities : " + entities.size() + ", in " + timeMS + " MS]");
        }
        
        // Convert "Set of Entities" to "Array of Entities (as JSON objects)".
        JSONObject entityObj = null;
        for(String entity : entities.keySet()){
            entityObj = new JSONObject();
            entityObj.put("entity", entity);
            entityObj.put("score", entitiesScores.get(entity));
            entityObj.put("relations", (JSONArray)entities.get(entity));
            entitiesFullArray.add(entityObj);
        }
        
        // Sort Array of entity objects by score.      
        Collections.sort(entitiesFullArray, new Comparator<JSONObject>() {
            private static final String KEY_NAME = "score";

            @Override
            public int compare(JSONObject a, JSONObject b) {
                float valA = (float) a.get(KEY_NAME);
                float valB = (float) b.get(KEY_NAME);
                return Float.compare(valB, valA);
                //if you want to change the sort order, simply use the following:
                //return -valA.compareTo(valB);
            }
        });

        // Select entities to be returned (Paging)
        int entitiesNum = entitiesFullArray.size();
        //page : [0, maxPage], maxPage = pages - 1.
        int pages = entitiesNum/entitiesPerPage; //full pages : [0, maxPage +1], Has value 0 only when there are no results at all!
        int restEntities = entitiesNum%entitiesPerPage; //docs of "last page" may not be enough for a whole page : [0 , articlesPerPage)
        if(restEntities != 0){ // an extra not full page exist (the last page)
            pages++; 
        }
        //In case of wrong page, use default page
        if(pages > 0){ // at least one page exists (there are some examples)
            if(page >= pages){
                // if page is too big, use last page
                page = pages - 1;
            } else if(page < 0){
                //if page is too small. use first page
                page = 0;
            } // else page is allready valid 
        } else { // no pages - no results
            // Return empty Response
            Date endDate = new Date();
            long timeMS = endDate.getTime()-startDate.getTime(); 
            return getResultJSON(entitiesResultArray, keywords, entitiesPerPage, page, timeMS);
        }
                
        // calculate index of first document of the page
        int hitsStart = page * entitiesPerPage; 
        int lastHitRequired = hitsStart; //to begin with 
        if(restEntities !=0 &(pages-page) == 1){//this is the last page and is not full of docs
            lastHitRequired += restEntities;
        } else { // it is a normal page full of docs
            lastHitRequired += entitiesPerPage;
        }

        int hitsEnd = Math.min(lastHitRequired, hitsStart + entitiesPerPage);
        
        // Update List with entities to be returned as results
        for (int i = hitsStart ; i < hitsEnd; i++) {
               entitiesResultArray.add(entitiesFullArray.get(i));
        }
        
        Date endDate = new Date();
        long timeMS = endDate.getTime()-startDate.getTime(); 
//        System.out.println(entities.size() + " entities found");
//        printTime(startDate,endDate,"Processing");
        
        String ResponseJSON = getResultJSON(entitiesResultArray, keywords, entitiesPerPage, page, timeMS);
        return ResponseJSON;
    }
     
    /**
     *  Search in indexes for keywords (call readTriples)
     *      In triples retrieved find "entities" and initialize entities and entitiesScore Sets with keys and empty values.
     * 
     * @param entities          To be initialized - keys only
     * @param entitiesScore     To be initialized - keys only
     * @param keywordsString    Search terms
     * @return                  Triples retrieved
     */
    private JSONArray findEntities( HashMap <String, JSONArray> entities, HashMap <String, Float> entitiesScore, String keywordsString){
        JSONArray triplesAsSearchField =  new JSONArray();
        
        // Search in all indexes for tripples
        for(int i =0 ; i < getSearchersNum() ; i++){
            triplesAsSearchField.addAll(readTriples( i, keywordsString));
        }        
        
//        System.out.println(triplesAsSearchField.size() + " triples retrieved for query : " + keywordsString );
        
        // Find "Entities"
        // all returned subjects and objects considered entities
        JSONObject tripleJSON = null;
        String object = "";
        String subject = "";
        for(Object triple : triplesAsSearchField){
            tripleJSON = (JSONObject)triple;
//            Modification if wanted : As entities, use subjects of triples with "label" predicates
//            if(labelPredicates.contains(predicate)){ // its a label triple
            object = (String)tripleJSON.get("obj");
            if(!entities.containsKey(object)){  // new entity found
                entities.put(object, new JSONArray());
                entitiesScore.put(object, 0F);
            }
            subject = (String)tripleJSON.get("subj");
            if(!entities.containsKey(subject)){  // new entity found
                entities.put(subject, new JSONArray());
                entitiesScore.put(subject, 0F);
            } // else, entity already exists in set
//            }
        }

        return triplesAsSearchField;
    }
    
    /**
     *  Update entities and entitiesScore HashMap with appropriate values
     *  1. Get a set of entities (keys of HasMap) 
     *  2. Find triples with this entities as Subject or Object.
     *  3. Add this triples in "JSONArray value" in entities Set and corresponding score to entitiesScore set.
     * 
     * @param entities          input/To be updated - set of entities 
     * @param entitiesScore     To be updated - set of corresponding values with "max Triple score" found in "JSONArray value"-elements. (for each entity)
     * @param triples           input - set of "relative triples" used to create "relation"/"JSONArray value"  
     */
    private void loadEntities( HashMap <String, JSONArray> entities, HashMap <String, Float> entitiesScore, JSONArray triples){
        Set <String> entitiesSet = entities.keySet();
        JSONObject tripleJSON = null;
        JSONObject responseTripleJSON = null;
        String entity = "";
        boolean entityFound = false;
        float score = 0;
        // Foreach triple
        for(Object triple : triples){
            tripleJSON = (JSONObject)triple;
            // if object is an entity
            if(entitiesSet.contains(tripleJSON.get("obj").toString())){
                // prepare a triple object to be added to "relations" of this entity
                responseTripleJSON = new JSONObject();
                entity = tripleJSON.get("obj").toString();
                responseTripleJSON.put("subj", tripleJSON.get("subj"));
                responseTripleJSON.put("pred", tripleJSON.get("pred"));
                score = (float)tripleJSON.get("score");     
                //TO DO : add labels
                // Add triple object to "relations" of this entity
                if(entities.get(entity).size() < maxTriplesInRelations){
                    // add a triple to entity
                    entities.get(entity).add(responseTripleJSON);
                    // update maxScore
                    if(entitiesScore.get(entity) < score){
                        entitiesScore.put(entity, score);
                    } 
                }
                entity = "";
                responseTripleJSON = null;
            } 
            
            // if subject is an entity
            if(entitiesSet.contains(tripleJSON.get("subj").toString())) {
                // prepare a triple object to be added to "relations" of this entity
                responseTripleJSON = new JSONObject();
                entity = tripleJSON.get("subj").toString();
                responseTripleJSON.put("obj", tripleJSON.get("obj"));
                responseTripleJSON.put("pred", tripleJSON.get("pred"));
                score = (float)tripleJSON.get("score");        
                //TO DO : add labels
                // Add triple object to "relations" of this entity
                if(entities.get(entity).size() < maxTriplesInRelations){
                    // add a triple to entity
                    entities.get(entity).add(responseTripleJSON);
                    // update maxScore
                    if(entitiesScore.get(entity) < score){
                        entitiesScore.put(entity, score);
                    } 
                }
                entity = "";
                responseTripleJSON = null;
            } 
        }
    }

    /**
     * Search an index for triples relative to query
     * 
     * @param index         The number of index to search (0 for "_0", 1 for "_1" etc) 
     * @param queryString   
     * @return              a JSONArray with triples retrieved
     */
    private JSONArray readTriples(int index, String queryString){  
        Query query = null;
        try {
            query = getParser(index).parse(queryString);
        } catch (ParseException ex) {
            System.out.println(" " + new Date().toString() + " ParseException in readTriples, queryString : " + queryString );
            ex.printStackTrace();
            return new JSONArray();
        }
        
        //Search and calculate the search time in MS
        Date startDateIn = new Date();
        // Collect hitsMax top Docs
        TopDocs results;
        try {
            results = getSearcher(index).search(query,TripleHitsMax);
        } catch (IOException ex) {
            System.out.println(" " + new Date().toString() + " IOException in readTriples, queryString : " + queryString );
            ex.printStackTrace();
            return new JSONArray();
        }
        Date endDateIn = new Date();
        long timeMS = endDateIn.getTime()-startDateIn.getTime();

        if(debugMode) {
//            System.out.println(" " + new Date().toString() + " Search (Triples) in " + index + " >  total matching Triples : " + results.totalHits + ", " + timeMS + " MS]");
        }      
        
        ScoreDoc[] hits = results.scoreDocs;

        return loadTriples(index, hits);
    }
    
    /**
     *  Move triples from "hits" array and to "triples" JSONArray 
     * @param index     Index searched
     * @param hits      input - array of search results
     * @return          JSONArray of triples retrieved 
     */
    private JSONArray loadTriples(int index, ScoreDoc[] hits) {
        JSONArray triples =  new JSONArray();
        Document doc = null;
        JSONObject obj = new JSONObject();
        for(int i = 0 ; i < hits.length ; i++){
            try {
                doc = getSearcher(index).doc(hits[i].doc);
            } catch (IOException ex) {
                System.out.println(" " + new Date().toString() + " IOException in loadTriples, index : " + index + ", i (hit) : " + i );
                ex.printStackTrace();
                return new JSONArray();
            }
            if (doc != null) {
                obj.put("subj", doc.get("subject"));                
                obj.put("obj", doc.get("object"));                
                obj.put("pred", doc.get("predicate"));
                obj.put("score", hits[i].score);
                triples.add(obj);
                obj = new JSONObject();
            }
        }
        return triples;
    }
    
    /**
     *  Prepare JSON Response String with results
     * 
     * @param entitiesJSONArray
     * @param keywords
     * @param conceptsPerPage
     * @param page
     * @param timeMS
     * @return          JSON Response for results
     */
    private String getResultJSON(JSONArray entitiesJSONArray, String keywords, int conceptsPerPage, int page, Long timeMS ) {

        JSONObject responseObject = new JSONObject();
            JSONObject result = new JSONObject();
                result.put("entities", entitiesJSONArray);
                result.put("keywords", keywords);
                result.put("page", page);
                result.put("conceptsPerPage", conceptsPerPage);
                result.put("timeMS", timeMS);
            responseObject.put("result", result);
        
        return responseObject.toString();
    }
       
    /**
     *  Convert given query to lucene syntax (first step)
     *      Removes special characters from query and updates variable keywords
     *      Parentheses currently not supported!!! 
     * @param query     input - keywords query 
     * @return          query in lucene format
     */
    private String queryToLucene(String query){   
        //replace special chars
        for(int i = 0 ; i < specialCharacters.size() ; i++){
           query = query.replace(specialCharacters.get(i), " ");
        }
        //replace all sequences os spaces tabs etc with just one space
        
        query = query.replaceAll("\\s+", " ");
         String luceneQuery = "";
//         ArrayList <String> parts = new <String> ArrayList();
// Parentheses not supported!!!         
         
//            //Find parts [between parentheses]
//            if(query.contains("(") & query.contains(")"))//parentheses in the string
//            {
//                String tmpStr = "";
//                String indexField ="";
//                boolean insideIndexField = false;
//
//                for(int i = 0; i < query.length() ; i++ ){
//                    if(query.charAt(i) == '(' | query.charAt(i) == ')') { // new part start
//                        tmpStr = tmpStr.trim();
//                        if(!tmpStr.equals("")){
//                            parts.add(handlePhrases(tmpStr));
//                            tmpStr = "";
//                        }
//                        parts.add(" " + query.charAt(i) + " ");
//                    } else if(query.charAt(i) == '[' & tmpStr.trim().equals("")) { // index field opening a part, refers to previus part (i.e. ( ... ...)[...] case)
//                        //handle index field here because the previus part will be not available to handlePhrases
//                        insideIndexField = true;
//                    } else if(insideIndexField) { 
//                        if(query.charAt(i) == ']'){// end of index field (opening a part)
//                            tmpStr = tmpStr.trim();
//                            if( !tmpStr.equals("")){ // add this index field to previus element
//                                String luceneField = supportedIndexFields.get(tmpStr);
//                                if(luceneField != null){ // field supported
//                                    boolean previusPartFound = false;
//                                    boolean previusIsParenthesis = false;
//                                    int j = parts.size()-1;
//                                    while(j >= 0 & !previusPartFound){ 
//                                        String prevPart = parts.get(j).trim();
//                                        if(prevPart.equals(")")){
//                                            previusIsParenthesis = true;
//                                        } else if(prevPart.equals("(") & previusIsParenthesis){ //beginig of previus paretheses reached
//                                            String prevClause = "";
//                                            for(int k = parts.size()-1; k >= j ; k--){
//                                                prevClause = parts.get(k) + prevClause;
//                                                parts.remove(k);
//                                            }
//                                            parts.add(luceneField + ":\"" + prevClause+"\"");
//                                            previusPartFound = true;
//                                        } else if(!previusIsParenthesis){ // not parentheses, it's a single term
//                                            parts.remove(j);// remove part without index field
//                                            parts.add( luceneField + ":\"" + prevPart +"\"");// add again with index field
//                                            previusPartFound = true;
//                                        }
//                                        j--;
//                                    }
//                                }
//                                tmpStr = "";
//                            }
//                            insideIndexField = false;
//                        } else {
//                             tmpStr += query.charAt(i);
//                        }
//                    } else { // continue existing part
//                        tmpStr += query.charAt(i);
//                    }
//                }
//                tmpStr = tmpStr.trim();
//                if(!tmpStr.equals("")){
//                    parts.add(handlePhrases(tmpStr));
//                }
//            } else { // no paretheses, do further handling
                luceneQuery = handlePhrases(query);        
//            }

            //handle boolean operators
//            boolean fisrtPhrase = true;
//            for(int i = 0 ; i < parts.size() ; i ++){
//                String currentPart = parts.get(i);
//                    if(!currentPart.startsWith(" OR ") & !currentPart.startsWith(" -") & !currentPart.startsWith(" +") & !currentPart.equals(" ) ")){
//                        if(fisrtPhrase){
//                            luceneQuery += " +";
//                            fisrtPhrase = false;
//                        } else {
//                            if(!parts.get(i-1).endsWith(" OR ") & !parts.get(i-1).endsWith(" -") & !parts.get(i-1).endsWith(" +")){
//                                luceneQuery += " +";
//                            }
//                        }                
//                    } // add default operator + when required (no OR or - is present for that term)
//                    luceneQuery += parts.get(i);
//            }

//        System.out.println(luceneQuery);
        return luceneQuery;                
    }
    
    /**
     *  Convert given query to lucene syntax (second step)
     *      Handle quotes, boolean operators and brackets
     * 
     * @param query     input - query string, cleaned from special characters
     * @return          query in lucene format
     */
    private static String handlePhrases(String query) {
        //Find phrases "between double quotes"
        String[] phrases = query.split("\"");
        for(int i = 0 ; i < phrases.length ; i++){
            phrases[i] = phrases[i].trim();
            if(!phrases[i].equals("")){ // String not Empty
                if(i%2 != 0){ // String surrounded by quotes, i.e. a phrase
                    phrases[i] = "\"" + phrases[i] + "\"";
                } else { // not a phrase, i.e. a bag of terms, operator or [field of index] (inside brackets)
                    if(phrases[i].startsWith("[")){ //index field of previus component contained (i.e. case: "..."[...])
                        boolean perviusPhraseFound = false; // True if index field was added to previus phrase element
                        // Get index field of previous component and handle proprietly
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
                                    String quote = "";
                                    if(tokens[tmp].contains(":")){// this is probably a URL...
                                        quote = "\"";
                                    }
                                    phrases[tmp] = luceneField + ":" + quote + phrases[tmp] + quote;
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
     *  Convert given query to lucene syntax (third step)
     *      Replace space with AND (default operator for lucene is OR)
     * 
     * @param stringPart
     * @return      query part in lucene format
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
                                String quote = "";
                                if(tokens[tmp].contains(":")){// this is probably a URL...
                                    quote = "\"";
                                }
                                tokens[tmp] = luceneField + ":" + quote + tokens[tmp] + quote ;
                            } // else : Lucene counterpart not found, unsupported field - do nothing
                        } else {
                            tmp--;
                        }
                    }
                    //Empty field to not be added as a search term in query
                    tokens[i] = "";
                } 
            }
        }
        
        String luceneQueryPart = "";
        boolean notWaiting = false;
        boolean orWaiting = false;
        for(int i = 0 ; i < tokens.length ; i++){
            if(tokens[i].contains(":") 
                & !tokens[i].startsWith(supportedIndexFields.get("subj"))
                & !tokens[i].startsWith(supportedIndexFields.get("obj"))
                & !tokens[i].startsWith(supportedIndexFields.get("pred"))){// this is probably a URL...
                    tokens[i] = "\"" + tokens[i] + "\"" ;
                }
            
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
     * @param i
     * @return the reader
     */
    public IndexReader getReader(int i) {
        return readers.get(i);
    }

    /**
     * @param reader the reader to set
     */
    public void setReader(IndexReader reader) {
        this.readers.add(reader);
    }

    /**
     * @param i
     * @return the searcher
     */
    public IndexSearcher getSearcher(int i) {
        return searchers.get(i);
    }

    /**
     * @return searchers' size
     */
    public int getSearchersNum() {
        return searchers.size();
    }
    
    /**
     * @param searcher the searcher to set
     */
    public void setSearcher(IndexSearcher searcher) {
        this.searchers.add(searcher);
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
     * @param i
     * @return the parser
     */
    public MultiFieldQueryParser getParser(int i) {
        return parsers.get(i);
    }

    /**
     * @param parser the parser to set
     */
    public void setParser(MultiFieldQueryParser parser) {
        this.parsers.add(parser);
    }
    
    /**
     * @return the indexPath
     */
    public String getIndexPath() {
        return indexPath;
    }

    /**
     * @param indexPath the indexPath to set
     */
    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

    /**
     * Print time of the operation in human readable form
     * @param start
     * @param end
     * @param job 
     */
    public static void printTime(Date start, Date end, String job){
     long miliseconds = end.getTime() - start.getTime();
            String totalTime = String.format("%02d:%02d:%02d", 
                TimeUnit.MILLISECONDS.toHours(miliseconds),
                TimeUnit.MILLISECONDS.toMinutes(miliseconds) - 
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(miliseconds)),
                TimeUnit.MILLISECONDS.toSeconds(miliseconds) - 
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(miliseconds)));
            //log printing
            System.out.println(" " + new Date().toString() + " Total " + job + " time : " + totalTime); 
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
    @Override
    public String getExampleJson() {
        return exampleJson;
    }

}
