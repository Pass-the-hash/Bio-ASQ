/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package searchService;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 *
 * @author tasosnent
 * 
 * Class inherited by all concept Searchers
 *      i.e. OBO Searchers and Uniprot searcher
 *      includes common methods used by those searchers 
 */
public abstract class ConceptSearcher extends Searcher {
    public abstract String searchId( String queryString) throws Exception;
    public abstract String getDefaultNamespace() throws Exception;;
    /**
     *  Creates a list of keywords, splitting a query string by spaces.
     * @param query
     * @return          ArrayList of keywords
     */
    protected ArrayList<String> queryToKeywords(String query, ArrayList<String> specialCharacters){   
        ArrayList<String> keywords = new ArrayList<String>();

        //convert query to a bag of keywords
        for(int i = 0 ; i < specialCharacters.size() ; i++){
           query = query.replace(specialCharacters.get(i), " ");
        }
        //replace all sequences os spaces tabs etc with just one space
        query = query.replaceAll("\\s+", " ");

        //update keywords variable
        String[] parts = query.split(" ");
        for(int i = 0 ; i < parts.length ; i++ ){
            keywords.add(parts[i].toLowerCase());
    //            keywordPatterns.add(Pattern.compile(".*(" + parts[i].toLowerCase() + ").*"));
        }

        return keywords;                
    }

    /**
     * Removes special characters from query and updates variable keywords
     * TO DO: Merge with synonym method in other files
     * @param query
     * @return      cleaned query
     */
    protected String cleanQuery(String query,ArrayList<String> specialCharacters){   
        //convert qiery to a bag of keywords
        for(int i = 0 ; i < specialCharacters.size() ; i++){
           query = query.replace(specialCharacters.get(i), " ");
        }
        //replace all sequences os spaces tabs etc with just one space
        query = query.replaceAll("\\s+", " ");
        
        return query;                
    }
    
    /**
     * Creates a list of Search Patterns for finding keywords 
     * TO DO: Merge with synonym method in other files
     * @param keywords
     * @return 
     */
    protected ArrayList<Pattern> keywordsToPatterns(ArrayList<String> keywords){   
        ArrayList<Pattern> keywordPatterns = new ArrayList<Pattern>(); 

        //update keywords variable
        for(int i = 0 ; i < keywords.size() ; i++ ){
            keywordPatterns.add(Pattern.compile(".*(" + keywords.get(i) + ").*"));
        }
        return keywordPatterns;                
    } 
}
