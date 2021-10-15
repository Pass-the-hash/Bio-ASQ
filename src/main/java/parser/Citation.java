package parser;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Citation {
    private Result result;

    public Citation(int hits, int page, int results){
         result=new Result(hits, page, results);
    }

    public Citation(){

    }

    class Result{
        private int articlesPerPage, page, size, time;
        private String query, keywords;
        private Date date;
        private List<Document> documents;

        public Result(int hits, int page, int results){
            documents=new LinkedList<Document>();
            articlesPerPage=hits;
            this.page=page;
            this.size=results;
        }


    }
    class Document{
        public Document(String pmid, String title, String documentAbstract){
            this.pmid=pmid;
            this.documentAbstract=documentAbstract;
            this.title=title;
        }

        private String documentAbstract, journal, pmid, sections=null, title, year;
        private boolean textAvailable;
        private List<Annotation> meshAnnotations=null;
        private List<String> meshHeading;

        private class Annotation{
            /*String label;
            URI uri;
            class URI{
                String id, namespace;
            }*/
        }
    }

    public void addDocument(String pmid, String title, String Abstract){
        result.documents.add(new Document(pmid, title, Abstract));
    }

}
