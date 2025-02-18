/**
 * Copyright (C) 2015, GIAYBAC
 *
 * Released under the MIT license
 */
package com.giaybac.traprange.test;

import com.giaybac.traprange.PDFTableExtractor;
import com.giaybac.traprange.entity.Table;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

/**
 *
 * @author THOQ LUONG Mar 22, 2015 5:36:40 PM
 */
public class TestExtractor {

    //--------------------------------------------------------------------------
    //  Members
    //--------------------------------------------------------------------------
    //  Initialization and releasation
    //--------------------------------------------------------------------------
    //  Getter N Setter
    //--------------------------------------------------------------------------
    //  Method binding
    @Test
    public void test() throws IOException {
        PropertyConfigurator.configure(TestExtractor.class.getResource("/com/giaybac/traprange/log4j.properties"));

        String homeDirectory = System.getProperty("user.dir");

        String sourceDirectory = Paths.get(homeDirectory, "_Docs").toString();
        String resultDirectory = Paths.get(homeDirectory, "_Docs", "result").toString();
        
        for (int idx = 0; idx < 1; idx++) {
            PDFTableExtractor extractor = (new PDFTableExtractor())
                    .setSource(sourceDirectory + File.separator + "hzdata1.pdf");
//                    .setSource(sourceDirectory + File.separator + "sample-" + (idx + 1) + ".pdf");
            switch (idx) {
                case 0: {
                    extractor.addPage(24);
                    List<Integer> a = new ArrayList<Integer>();
                    // a.add(0);
                    //a.add(-1);
                    /*
                    for (int i=0; i<23; i++) {
                        a.add(i);
                    }
                    /*
                    for (int j = 6; j< 30;j++) {
                        a.add(j);
                    }
                    */

                    int[] array = new int[a.size()];
                    for (int aidx=0;aidx<a.size();aidx++) {
                        array[aidx] = a.get(aidx);
                    }
                    // a.stream().mapToInt(i->i).toArray();
                    extractor.exceptLine(array);
                    break;
                }
                case 1: {
                    extractor.exceptLine(new int[]{0, 1});
                    break;
                }
                case 2: {
                    extractor.exceptPage(0)
                            .exceptLine(new int[]{0});
                    break;
                }
                case 3: {
                    extractor.exceptLine(new int[]{0});
                    break;
                }
                case 4: {
                    extractor.exceptLine(0, new int[]{0, 1});
                    break;
                }
            }
            List<Table> tables = extractor.extract();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(resultDirectory + "//sample-" + (idx + 1) + ".html"), "UTF-8")) {
                for (Table table : tables) {
                    writer.write("Page: " + (table.getPageIdx() + 1) + "\n");
                    writer.write(table.toHtml());
                }
            }
        }
    }
    //--------------------------------------------------------------------------
    //  Implement N Override
    //--------------------------------------------------------------------------
    //  Utils
    //--------------------------------------------------------------------------
    //  Inner class
}
