package io;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class CSVManager {
    private String csv;

    public CSVManager(String csv) {
        this.csv=csv;
    }

    public List<String[]> Read()  {
        CSVReader reader;
        System.out.println("Locating file...");
        try {
           reader = new CSVReader(new FileReader(csv));
        } catch (Throwable e) {
            System.out.println("File not found!");
            e.printStackTrace();
            return null;
        }
        System.out.println("Trying to read...");
        try {
            return reader.readAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //List entries=new List();
        return null;
    }

    //To implement Write()...
}
