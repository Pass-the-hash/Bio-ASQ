/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package searchService;

import org.json.simple.JSONArray;

/**
 *
 * @author tasosnent
 * 
 * Class inherited by all searchers 
 *      contains common helper methods
 */
public abstract class Searcher {
    public abstract String search( String queryString, int page, int docsPerPage) throws Exception;
    public abstract String getName();
    public abstract String getExampleJson();
    
   /**
    * Convert an Array of String to a JSONList
    * @param StrArr
    * @return 
    */  
   protected static JSONArray StringArrayToJSONList(String[] StrArr){
       JSONArray List = new JSONArray();
           for(int i=0 ; i < StrArr.length; i++){
               List.add(StrArr[i]);
           }
       return List;
   }
    
   /**
    * Convert an Array of String to a String
    * @param StrArr
    * @return 
    */
   protected static String StringArrayToString(String[] StrArr){
       String str = new String();
           for(int i=0 ; i < StrArr.length; i++){
               str += StrArr[i];
           }
       return str;
   }   
}
