/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package searchService;

import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
*
* @author tasosnent
* 
*   Example request with JSON: 
        Documents search		
           empty results		:   curl -d "json={\"findPubMedCitations\": [\"transcription[Abstract] \\\"factor foxp3\\\"[TI] boy\",10,5]}" 
           empty results		:   curl -d "json={\"findPubMedCitations\": [\"2565400000\", 10, 5]}" 
           results			:   curl -d "json={\"findPubMedCitations\": [\"transcription factor foxp3\", 10, 5]}"  http://localhost:8000/2?-1cc93400%3A15917d92965%3A-8000
           results			:   curl -d "json={\"findPubMedCitations\": [\"25671254\", 10, 5]}"   
                   json exception		:   curl -d "json={\"findPubMedCitations\": [\"2565400000\", 10]}" 
                   json exception		:   curl -d "json={\"retrieveEntities\": [\"DNA\", 10, 5]}" 
                   json exception		:   curl -d "json={\"findEntitiesPaged\": [\"DNA\", 10, 5]}" 
                   session exception	:   curl -d "json={\"findPubMedCitations\": [\"25671254\", 10, 5]}" http://localhost:8000/2
                   session exception	:   curl -d "json={\"findPubMedCitations\": [\"25671254\", 10, 5]}" http://localhost:8000/2?54646
        Concepts search (Mesh, GO, DO, jochem and uniprot)
           results			:   curl -d "json={\"findEntitiesPaged\": [\"DNA\", 10, 5]}" 
           results GO id          	:   curl -d "json={\"retrieveEntities\": [\"http://amigo.geneontology.org/amigo/term/GO:1901536\", 10, 5]}" 
                                        :   curl -d "json={\"retrieveEntities\": [\"http://www.nlm.nih.gov/cgi/mesh/2017/MB_cgi?field=uid&exact=Find+Exact+Term&term=D006627\", 10, 5]}" 
                   json exception		:   curl -d "json={\"findPubMedCitations\": [\"DNA\", 10, 5]}" 
                   json exception		:   curl -d "json={\"find\": [\"DNA\", 10, 5]}" 
        triples Search  (linked life data)
           results			:   curl -d "json={\"findEntitiesPaged\": [\"DNA\", 5, 5]}" 
                   json exception		:   curl -d "json={\"find\": [\"DNA\", 10, 5]}" 
                   json exception		:   curl -d "json={\"retrieveEntities\": [\"DNA\", 10, 5]}" 
                   json exception		:   curl -d "json={\"findPubMedCitations\": [\"DNA\", 10, 5]}" 
*/
public class SearchService {
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private final static int    port = 8000; 
    // Commented for Server compile 
    //*
    private final static String host = "http://localhost"; 
//    private final static String triplesIndexPath =   "D:\\00 indexes Used\\BioASQ8\\Triples index\\Triples_index_20160318";// ATTENTION: "Triples_index_20160318_0", "Triples_index_20160318_1" are the real names of indexes on disc
//    private final static int triplesIndexParts = 2; //   triples index is actually more than one indexes due to size restriction in lucene. SearchTriples handles this fact and searches in all indexes provided, based on a naming convention.  
//    private final static String documentsIndexPath = "D:\\indexes Used\\Frozen index Used 20160113\\Documents_index";
    private final static String documentsIndexPath = "D:\\00 indexes Used\\BioASQ8\\Documents index MBR2020\\Documents_index";
//    private final static String meshIndexPath = IndexFolderPath + "Mesh_index"; // deprecated : synonym field cleaned ( quotes and "[]" removed)
//    private final static String oboIndexFolderPath = "D:\\indexes Used\\OBO indexes Used 20160225\\";
    private final static String oboIndexFolderPath = "D:\\00 indexes Used\\BioASQ8\\OBO indexes\\";
    private final static String meshIndexPath = oboIndexFolderPath + "Mesh_index";
    private final static String jochemIndexPath = oboIndexFolderPath + "Jochem_index";
    private final static String goIndexPath =   oboIndexFolderPath + "GO_index";
    private final static String doIndexPath =   oboIndexFolderPath + "DO_index";
    private final static String uniprotIndexPath =   oboIndexFolderPath + "Uniprot_index";
    //*/
    // Uncommented for Server compile 
    /*
   // private final static String host = "http://143.233.226.92"; 
    private final static String host = "http://bioasq.org"; 
    private final static String triplesIndexPath =   "/home/bioasqer/projects/indexes/BioASQ7/Triples index/Triples_index_20160318";// ATTENTION: "Triples_index_0", "Triples_index_1" are the real names of indexes on disc
    private final static int triplesIndexParts = 2; //   triples index is actually more than one indexes due to size restriction in lucene. SearchTriples handles this fact and searches in all indexes provided, based on a naming convention.  
    private final static String documentsIndexPath = "/home/bioasqer/projects/indexes/BioASQ7/Documents_index";
    private final static String meshIndexPath = "/home/bioasqer/projects/indexes/BioASQ7/Mesh_index"; 
    private final static String jochemIndexPath = "/home/bioasqer/projects/indexes/BioASQ7/Jochem_index";
    private final static String goIndexPath =   "/home/bioasqer/projects/indexes/BioASQ7/GO_index";
    private final static String doIndexPath =   "/home/bioasqer/projects/indexes/BioASQ7/DO_index";
    private final static String uniprotIndexPath =   "/home/bioasqer/projects/indexes/BioASQ7/Uniprot_index";
    //*/
//    private final static String triplesContext = "/triples"; 
//    private final static String searchTriplesContext = "/triples/2"; 
//    private static SearchTriples triplesSearcher = null;
    
    private final static String documentsContext = "/pubmed"; 
    private final static String searchDocumentsContext = "/2"; 
    private static SearchDocuments documentsSearcher = null;
    
    private final static String meshContext = "/mesh"; 
    private final static String searchMeshContext = "/mesh/2"; 
//    private static SearchMesh meshSearcher = null;
    private static SearchOBO meshSearcher = null;
    private final static String meshDefaultNameSpace = "https://meshb.nlm.nih.gov/record/ui?ui="; 
//    private final static String meshDefaultNameSpace = "https://www.nlm.nih.gov/cgi/mesh/2017/MB_cgi?field=uid&exact=Find+Exact+Term&term="; 
//    private final static String meshDefaultNameSpace = "http://www.nlm.nih.gov/cgi/mesh/2016/MB_cgi?field=uid&exact=Find+Exact+Term&term="; 
    //extra namespace used
//    private final static String meshOldNameSpace = "http://www.nlm.nih.gov/cgi/mesh/2012/MB_cgi?field=uid&exact=Find+Exact+Term&term="; 
    
   /* private final static String jochemContext = "/jochem";
    private final static String searchJochemContext = "/jochem/2"; 
    private static SearchOBO jochemSearcher = null;
    private final static String jochemDefaultNameSpace = "http://www.biosemantics.org/jochem#"; 
    
    private final static String doContext = "/do"; 
    private final static String searchDOContext = "/do/2"; 
    private static SearchOBO doSearcher = null;
    private final static String doDefaultNameSpace = "http://www.disease-ontology.org/api/metadata/DOID:"; 
    
    private final static String goContext = "/go"; 
    private final static String searchGOContext = "/go/2"; 
    private static SearchOBO goSearcher = null;
    private final static String goDefaultNameSpace = "http://amigo.geneontology.org/amigo/term/GO:"; 
    //extra namespace used
//    private final static String goNameSpace2 = "http://amigo.geneontology.org/cgi-bin/amigo/term_details?term="; 
//    private final static String goNameSpace2 = "http://amigo.geneontology.org/cgi-bin/amigo/term_details?term=GO:"; 
    
    private final static String uniprotContext = "/uniprot"; 
    private final static String searchUniprotContext = "/uniprot/2"; 
    private static SearchUniprot uniprotSearcher = null;
    private final static String uniprotDefaultNameSpace = "http://www.uniprot.org/uniprot/"; */

    private final static String CORDContext = "/local";
    private final static String searchCORDContext = "/local/2";
    private static SearchCORD CORDSearcher = null;
    //private final static String CORDDefaultNameSpace = "http://www.uniprot.org/uniprot/";

//    Time out Management
    private final static long timeOut = 600000; //10 Minutes in MilliSeconds
    private static HashMap <String, Date> sessionIDs = new HashMap <String, Date> ();
    
    /**
     * Create a server for searching given lucene indexes
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        //Create HTTP Server object
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println(" " + new Date().toString() + " SearchService > HttpServer Created in host : " + host + ":" + port);
        //Bind Server object with Handler 
//        server.createContext(triplesContext, new SessionTriplesHandler());
//        server.createContext(searchTriplesContext, new SearchTriplesHandler());

        /*server.createContext(documentsContext, new SessionDocumentsHandler());
        server.createContext(searchDocumentsContext, new SearchDocumentsHandler());
        
        server.createContext(meshContext, new SessionMeshHandler());
        server.createContext(searchMeshContext, new SearchMeshHandler());
        
        server.createContext(jochemContext, new SessionJochemHandler());
        server.createContext(searchJochemContext, new SearchJochemHandler());        
        
        server.createContext(doContext, new SessionDOHandler());
        server.createContext(searchDOContext, new SearchDOHandler());

        server.createContext(goContext, new SessionGOHandler());
        server.createContext(searchGOContext, new SearchGOHandler());        

        server.createContext(uniprotContext, new SessionUniprotHandler());
        server.createContext(searchUniprotContext, new SearchUniprotHandler()); 
*/
        CORDSearcher=new SearchCORD("/tmp/directory", "/home/john/Downloads/cord-19_2020-06-19/2020-06-19/metadata.csv");
        System.out.println("Created searcher...");

        server.createContext(CORDContext, new SessionCORDHandler());
        System.out.println("Created session handler...");
        server.createContext(searchCORDContext, new SearchCORDHandler());
        System.out.println("Created search handler...");
        //Creates a default executor
        server.setExecutor(null); 
        //Start service
        server.start();

        //Create triples searcher
//        try{
//            triplesSearcher = new SearchTriples(triplesIndexPath,triplesIndexParts);
//            //log printing
//            System.out.println(" " + new Date().toString() + " SearchService > triplesSearcher Created for " + triplesIndexParts + " indexes with base Path : " + triplesIndexPath);
//        } catch (Exception e) {
//            //log printing
//            System.out.println(" " + new Date().toString() + " SearchService > caught a " + e.getClass() + "\n with message: " + e.getMessage() + "\n trying to create Searcher for :" + documentsIndexPath);
//        }
        
        //Create documents searcher
       /* try{
            documentsSearcher = new SearchDocuments(documentsIndexPath);
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > documentsSearcher Created for indexPath : " + documentsIndexPath);
        } catch (Exception e) {
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > caught a " + e.getClass() + "\n with message: " + e.getMessage() + "\n trying to create Searcher for :" + documentsIndexPath);
        }
        //Create OBO searchers       
       meshSearcher = createOBOSearcher( meshIndexPath, meshDefaultNameSpace);
        meshSearcher.setName("Mesh");
//        meshSearcher.addNamespace(meshOldNameSpace);
        jochemSearcher = createOBOSearcher( jochemIndexPath, jochemDefaultNameSpace);
        jochemSearcher.setName("jochem");
        goSearcher = createOBOSearcher( goIndexPath, goDefaultNameSpace);
        goSearcher.setName("GO");
//        goSearcher.addNamespace(goNameSpace2);
        doSearcher = createOBOSearcher( doIndexPath, doDefaultNameSpace);
        doSearcher.setName("DO");
        try{
            uniprotSearcher = new SearchUniprot(uniprotIndexPath, uniprotDefaultNameSpace);
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > UniprotSearcher Created for indexPath : " + uniprotIndexPath);
        } catch (Exception e) {
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > caught a " + e.getClass() + "\n with message: " + e.getMessage() + "\n trying to create Searcher for :" + uniprotIndexPath);
        }*/

    }
    /**
     * Generalized creation of a OBO-Searcher object
     * @param indexPath
     * @param defaultNameSpace
     * @return searcher created 
     */
    public static SearchOBO createOBOSearcher(String indexPath, String defaultNameSpace){
        SearchOBO searcher = null;
        try{
            searcher = new SearchOBO(indexPath, defaultNameSpace);
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > oboSearcher Created for indexPath : " + indexPath);
        } catch (Exception e) {
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > caught a " + e.getClass() + "\n with message: " + e.getMessage() + "\n trying to create Searcher for :" + indexPath);
        } 
        return searcher;
    }

    /**
     * Handling of rest session and search requests is done uniformly
     *      by handleSession and handleSearch respectively
     */
    static class SessionDocumentsHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchDocumentsContext);
       }
     }
    
//    static class SessionTriplesHandler implements HttpHandler {
//       @Override
//       public void handle(HttpExchange t) throws IOException {
//            handleSession( t, searchTriplesContext);
//       }
//    }    
    
   /* static class SessionJochemHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchJochemContext);
       }
    }

    static class SessionDOHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchDOContext);           
       }
    }
 
    static class SessionGOHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchGOContext);
       }
    }
    
    static class SessionMeshHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchMeshContext);
       }
    }     

    static class SessionUniprotHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchUniprotContext);
       }
    }*/

    static class SessionCORDHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
            handleSession( t, searchCORDContext);
       }
    }
    /*static class SearchDocumentsHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,documentsSearcher);
       }
   }

//    static class SearchTriplesHandler implements HttpHandler {
//       @Override
//       public void handle(HttpExchange t) throws IOException {
//           handleSearch(t,triplesSearcher);
//       }
//     }

    static class SearchMeshHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,meshSearcher);
       }
     }

    static class SearchJochemHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,jochemSearcher);
       }
     }
    
    static class SearchDOHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,doSearcher);
       }
     }
    
    static class SearchGOHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,goSearcher);
       }
     }

    static class SearchUniprotHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,uniprotSearcher);
       }
     }*/

     static class SearchCORDHandler implements HttpHandler {
       @Override
       public void handle(HttpExchange t) throws IOException {
           handleSearch(t,CORDSearcher);
       }
     }
    
    /**
    * Handles a session request 
    * @param t
    * @param searchContext
    * @throws IOException 
    */ 
    static void handleSession(HttpExchange t, String searchContext) throws IOException {
        String response = "";
            String sessionID = generateSessionId();

            //remove expired sessionIDs
            clearSessionIDs();
            sessionIDs.put(sessionID,new Date());

            //send whole url with session
                response = host + ":" + port + searchContext + "?" + sessionID;
            
            //prepair Responce Headers
            Headers headers = t.getResponseHeaders();
            
            headers.add("Content-Type", "application/json; charset=UTF-8");
//            headers.add("Connection", "keep-alive");          
//            headers.add("Content-Type", "multipart/form-data;application/json");
//            headers.add("Allow", "GET, POST, HEAD, PUSH");

            //Send Responce
            t.sendResponseHeaders(200, response.getBytes("UTF-8").length);

            // Log printing in debugMode
            if(debugMode) {
             // System.out.println(" " + new Date().toString() + " SearchService > Response session URL send : " + response + " SearchContex " + searchCORDContext);
            }

            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
    }
    
    /**
     * Handles a Search request 
     *      Attention: "triple search" and "document search" does not support "search by ID", 
     *      Therefore, no calls for with "retrieveEntities" key for "triple search" are allowed.
     * 
     * @param t
     * @param searcher      
     * @throws IOException 
     */
    private static void handleSearch(HttpExchange t, Searcher searcher) throws IOException {
            String response = "";
            //Check HTTP method

            String method = t.getRequestMethod();
            //get GET data
            String sessionID = t.getRequestURI().getQuery();//sessionID
//                System.out.println(" getData :" + sessionID);
            if(sessionID != null){ // a sessionid is send
                //remove expired sessionIDs
                clearSessionIDs();
                sessionID = java.net.URLEncoder.encode(sessionID, "UTF-8");
                if(sessionIDs.containsKey(sessionID)){ //session is valid
                    //update last visit
                    sessionIDs.put(sessionID, new Date());
//                    if(sessionIDs.contains(java.net.URLEncoder.encode(sessionID, "UTF-8"))){ //access allowed
                    //get POST data
                    InputStreamReader isr =  new InputStreamReader(t.getRequestBody(),"UTF-8");
                    BufferedReader br = new BufferedReader(isr);

                    // From now on, the right way of moving from bytes to utf-8 characters:

                    int b;
                    StringBuilder buf = new StringBuilder(512);
                    while ((b = br.read()) != -1) {
                        buf.append((char) b);
                    }

                    br.close();
                    isr.close();
                    String post = buf.toString();

                    // The resulting string is: buf.toString()
                    // and the number of BYTES (not utf-8 characters) from the body is: buf.length()
                    post = java.net.URLDecoder.decode(post,"UTF-8");

                    //Get PSOT parameter named "json"
                    String json = getJsonParameter(post);
                    //System.out.println(json);
                    if(json != null){
                        // Find calling mode "retrieveEntities" or "findEntitiesPaged"/"findPubMedCitations"
                            // ATTENTION : For searchTriples and searchDocuments no retrieveEntities is supported
                        if(json.contains("retrieveEntities") // It's probably "retrieveEntities" for a concept searcer
                                && !json.contains("findEntitiesPaged") && !json.contains("findPubMedCitations") // For case both fields are send in a multipart request
                                && !searcher.getName().equals(documentsSearcher.getName())
//                                & !searcher.getName().equals(triplesSearcher.getName())                                
                                ){ // no "retrieveEntities" is supported for triple and documents search
                        // it's a retrieveEntities call (probably from import assessment scripts) not a "normal participant call"
                            System.out.println("1st");
                            try { // unsafe operations based on correct format of JSON objects
                                JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException(json);
                                JSONArray request = (JSONArray) parsedRequest.get("retrieveEntities");
                                String uri = (String) request.get(0);// concept url
                                String id = uri; // find concepts id
                                ConceptSearcher cSearcher = (ConceptSearcher)searcher;
                                id = id.replace(cSearcher.getDefaultNamespace(), "");

                                // Log printing
                                if(debugMode) {
                                    System.out.println(" " + new Date().toString() + " SearchService > " + " " + cSearcher.getName() + " query by id request arrived : [" +"query id : " + id + "]");                                                
                                }
                                try{// search existing index by id
                                    // Note : "page" and "concepts per page" are ignored in "retrieveEntities" mode
                                    response = cSearcher.searchId(id); 
                                } catch (Exception e) {
                                    // Log printing
                                    System.out.println(" " + new Date().toString() + " SearchService > " + cSearcher.getName() + " caught a (search)" + e.getClass() + "\n with message: " + e.getMessage());                                                
                                    response = getExceptionJSON(e.toString());
                                }
                            } catch (Exception ex) {
                                response = getExceptionJSON(" Empty or wrong formated JSON object. " + searcher.getExampleJson());
                            }
                        } else {// it should be a "findEntitiesPaged" or "findPubMedCitations" call
                            System.out.println(searcher.getName());
                            String callingMode = "findEntitiesPaged"; // initialize as search for entities (concepts or triples use the same calling mode "findEntitiesPaged")
                            // if searching for articles mode is "findPubMedCitations"
                            if (json.contains("findPubMedCitations")) callingMode = "findPubMedCitations";

                            /*if(searcher.getName().equals("Document Searcher") || searcher.getName().equals("CORD Searcher")){
                                callingMode = "findPubMedCitations"; // its for articles
                            }*/

                            try { // unsafe operations based on correct format of JSON objects
                                JSONObject parsedRequest = (JSONObject) JSONValue.parseWithException(json);
                                JSONArray request = (JSONArray) parsedRequest.get(callingMode);
                                String query = (String) request.get(0);
                                long page = (long) request.get(1);
                                long docsPerPage = (long) request.get(2);

                                if(debugMode) {// Log printing
                                    System.out.println(" " + new Date().toString() + " SearchService > " + searcher.getName() + " query request arrived : [" +"query : " + query + ", page : " + page + ", docsPerPage : " + docsPerPage + " ]");                                                
                                }
                                try{//Search in existing index
                                    response = searcher.search( query, (int) page, (int)docsPerPage);
                                } catch (Exception e) {
                                    // Log printing
                                    System.out.println(" " + new Date().toString() + " SearchService > " + searcher.getName() + " caught a (search)" + e.getClass() + "\n with message: " + e.getMessage());                                                
                                    response = getExceptionJSON(e.toString());
                                }
                            } catch (Exception ex) {// JSON format error
                                response = getExceptionJSON(" Empty or wrong formated JSON object. " + searcher.getExampleJson());
                            }
                        }
                    } else { //No "json" parameter found in POST data
                        response = getExceptionJSON(" POST data should contain a json parameter. " + searcher.getExampleJson());
                    }
                } else { // sessionID wrong, no access allowed
                    response = getExceptionJSON(" Wrong session data, please try again. ");
                }
            } else { //no session code given
                //send whole url with session
                response = getExceptionJSON(" Empty GET data, a session code should be given in GET data of request. ");
            }
            response=response + "\n";

            //prepair Responce Headers
            Headers headers = t.getResponseHeaders();
            headers.add("Content-Type", "application/json; charset=UTF-8");
//            headers.add("Connection", "keep-alive");
//            headers.add("Content-Type", "multipart/form-data;application/json");
//            headers.add("Allow", "GET, POST, HEAD, PUSH");
            
            //Send Responce
            t.sendResponseHeaders(200, 0); //Zero means : let the client find out how many bytes is the response
//            t.sendResponseHeaders(200, response.getBytes("UTF-8").length); // this calculation of length is wrong (not taking into account RUL encoding?) so don't use it because causes error in nodejs, restart in AnnotationTool and loss of connection/session for logged in users

            // Log printing
//            if(debugMode) {System.out.println(" " + new Date().toString() + " SearchService > Response json send : " + response );}
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();    
    }
    
    /**
     * Find and return parameter named json in post data 
     *      TO DO : improve it, using a single regEx
     * @param post      
     * @return 
     */
    private static String getJsonParameter(String post) {
        // for supprot of multipart requests
            // TO DO : add here retieveEntities too? Not, retieveEntities is to be used by assessment import script only.
        if(post.contains("name=\"json\"")){
            String [] tokens = post.split("\n");     
                for(int i=0; i < tokens.length; i++){
                    if(tokens[i].contains("\"findPubMedCitations\"")){
                        return tokens[i];
                    } else if(tokens[i].contains("\"findEntitiesPaged\"")){
                        return tokens[i];
                    }
                }
        } else {
            //get charactes after "json=" delimiter
            String [] tokens = post.split("\\s*json\\s*=\\s*");
            if(tokens.length > 1){
                //remove characters after "}" delimiter
                return tokens[1].split("}")[0] + "}";
            }
        }
        //if "json=" delimiter not found, return null
        return null;
    }

    /**
     * Prepares JSON Response String for case an Exception is thrown
     * 
     * @param description   String description of the exception
     * @return 
     */
    private static String getExceptionJSON(String description) {
        JSONObject responseObject = new JSONObject();
            JSONObject exception = new JSONObject();
                exception.put("description", description );
        responseObject.put("exception", exception);
        return responseObject.toString();
    }
    
    /**
     * Create a random unique session id (URL encoded)
     * @return 
     */
    private static String generateSessionId() {
        String uid = new java.rmi.server.UID().toString();  // guaranteed unique
        try {
            return java.net.URLEncoder.encode(uid, "UTF-8");  // encode any special chars
        } catch (UnsupportedEncodingException ex) {
//            Logger.getLogger("TestService").log(Level.SEVERE, null, ex);
            //log printing
            System.out.println(" " + new Date().toString() + " SearchService > Error in URL Encoding (UTF-8) - generateSessionId");
        }
        return uid;
    }

    /**
     * Remove "old session IDs"(*) from list of valid session ids
     *      (*)not used for more than 10'
     */
    private static void clearSessionIDs() {
        // Log printing in debugMode
        if(debugMode) {
//            System.out.println(" " + new Date().toString() + " SearchService > Session ID clear started : " + sessionIDs.size() + " sessionIDs found");                                                
        }
        // sessionIDs to remove from valid sessionIDs set
        ArrayList <String> expiredSessionIDs = new ArrayList <String> ();
            
        Iterator keys = sessionIDs.keySet().iterator();
        String key = null;
        Date now = null;
        Long timeIdle = null;
        
         //for each sessionID
        while(keys.hasNext()){
            key = (String)keys.next();
            now = new Date();
            //calculate time since last visit (i.e. idle time)
            timeIdle = now.getTime() - sessionIDs.get(key).getTime();
            
            //if idle time more than limit selected
            if(timeIdle > timeOut ){
                //add this sessionID for removal
                expiredSessionIDs.add(key);
                // Log printing in debugMode
                if(debugMode) {
//                   System.out.println(" " + new Date().toString() + " SearchService > session ID expired : " + key);                                                
                }
            }
        }
        
        //remove expired sessionIDs
        for(int i = 0 ; i < expiredSessionIDs.size() ; i++){
            sessionIDs.remove(expiredSessionIDs.get(i));
        }
        // Log printing in debugMode
        if(debugMode) {
//            System.out.println(" " + new Date().toString() + " SearchService > Session ID clear ended : " + sessionIDs.size() + " sessionIDs left");                                                
        }
    }

}
