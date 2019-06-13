/**
 * Copyright (C) 2015, GIAYBAC
 *
 * Released under the MIT license
 */
package com.giaybac.traprange;

import com.giaybac.traprange.entity.Table;
import com.giaybac.traprange.entity.TableCell;
import com.giaybac.traprange.entity.TableRow;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.util.Pair;
import org.apache.pdfbox.rendering.PageDrawerParameters;

/**
 *
 * @author THOQ LUONG Mar 22, 2015 3:34:29 PM
 */
public class PDFTableExtractor {

    // --------------------------------------------------------------------------
    // Members
    private final Logger logger = LoggerFactory.getLogger(PDFTableExtractor.class);
    // contains pages that will be extracted table content.
    // If this variable doesn't contain any page, all pages will be extracted
    private final List<Integer> extractedPages = new ArrayList<>();
    private final List<Integer> exceptedPages = new ArrayList<>();
    // contains avoided line idx-s for each page,
    // if this multimap contains only one element and key of this element equals -1
    // then all lines in extracted pages contains in multi-map value will be avoided
    private final Multimap<Integer, Integer> pageNExceptedLinesMap = HashMultimap.create();

    private InputStream inputStream;
    private PDDocument document;
    private String password;

    /*
     * public static PrintWriter stdout = new PrintWriter( new
     * OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
     */

    // --------------------------------------------------------------------------
    // Initialization and releasation
    // --------------------------------------------------------------------------
    // Getter N Setter
    // --------------------------------------------------------------------------
    // Method binding
    public PDFTableExtractor setSource(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    public PDFTableExtractor setSource(InputStream inputStream, String password) {
        this.inputStream = inputStream;
        this.password = password;
        return this;
    }

    public PDFTableExtractor setSource(File file) {
        try {
            return this.setSource(new FileInputStream(file));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Invalid pdf file", ex);
        }
    }

    public PDFTableExtractor setSource(String filePath) {
        return this.setSource(new File(filePath));
    }

    public PDFTableExtractor setSource(File file, String password) {
        try {
            return this.setSource(new FileInputStream(file), password);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Invalid pdf file", ex);
        }
    }

    public PDFTableExtractor setSource(String filePath, String password) {
        return this.setSource(new File(filePath), password);
    }

    /**
     * This page will be analyze and extract its table content
     *
     * @param pageIdx
     * @return
     */
    public PDFTableExtractor addPage(int pageIdx) {
        extractedPages.add(pageIdx);
        return this;
    }

    public PDFTableExtractor exceptPage(int pageIdx) {
        exceptedPages.add(pageIdx);
        return this;
    }

    /**
     * Avoid a specific line in a specific page. LineIdx can be negative number, -1
     * is the last line
     *
     * @param pageIdx
     * @param lineIdxs
     * @return
     */
    public PDFTableExtractor exceptLine(int pageIdx, int[] lineIdxs) {
        for (int lineIdx : lineIdxs) {
            pageNExceptedLinesMap.put(pageIdx, lineIdx);
        }
        return this;
    }

    /**
     * Avoid this line in all extracted pages. LineIdx can be negative number, -1 is
     * the last line
     *
     * @param lineIdxs
     * @return
     */
    public PDFTableExtractor exceptLine(int[] lineIdxs) {
        this.exceptLine(-1, lineIdxs);
        return this;
    }

    public List<Table> extract() {
        List<Table> retVal = new ArrayList<>();
        Multimap<Integer, Range<Float>> pageIdNLineRangesMap = LinkedListMultimap.create();
        Multimap<Integer, TextPosition> pageIdNTextsMap = LinkedListMultimap.create();
        try {
            this.document = this.password != null ? PDDocument.load(inputStream, this.password)
                    : PDDocument.load(inputStream);
            PageProcessor thePageProcessor = new PageProcessor(document, pageNExceptedLinesMap);

            for (int pageId = 0; pageId < document.getNumberOfPages(); pageId++) {
                boolean need_extract = !exceptedPages.contains(pageId)
                        && (extractedPages.isEmpty() || extractedPages.contains(pageId));
                if (need_extract) {
                    thePageProcessor.processPage(pageId, pageIdNLineRangesMap, pageIdNTextsMap);

                    LineCatcher test = new LineCatcher(document.getPage(pageId));
                    test.processPage(document.getPage(pageId));
                }
            }

            // Calculate columnRanges
            List<Range<Float>> columnRanges = getColumnRanges(pageIdNTextsMap.values());
            for (int pageId : pageIdNTextsMap.keySet()) {
                Collection<TextPosition> pageContent = pageIdNTextsMap.get(pageId);
                Collection<Range<Float>> rowRanges = pageIdNLineRangesMap.get(pageId);

                Table table = buildTable(pageId, (List) pageContent, 
                                    (List) rowRanges, columnRanges);
                retVal.add(table);
                // debug
                logger.debug("Found " + table.getRows().size() + " row(s) and " + columnRanges.size()
                        + " column(s) of a table in page " + pageId);
            }

        } catch (IOException ex) {
            throw new RuntimeException("Parse pdf file fail", ex);
        } finally {
            if (this.document != null) {
                try {
                    this.document.close();
                } catch (IOException ex) {
                    logger.error(null, ex);
                }
            }
        }
        return retVal;
    }

    public List<RenderedImage> getImagesFromPDF(PDDocument document, int pageid) throws IOException {
            
        List<RenderedImage> images = new ArrayList<>();
        PDPage page = document.getPage(pageid);
        images.addAll(getImagesFromResources(page.getResources()));
    
        return images;
    }
    
    private List<RenderedImage> getImagesFromResources(PDResources resources) throws IOException {
        
        List<RenderedImage> images = new ArrayList<>();
    
        for (COSName xObjectName : resources.getXObjectNames()) {
            
            PDXObject xObject = resources.getXObject(xObjectName);
    
            if (xObject instanceof PDFormXObject) {
                images.addAll(getImagesFromResources(((PDFormXObject) xObject).getResources()));
            } else if (xObject instanceof PDImageXObject) {
                images.add(((PDImageXObject) xObject).getImage());
            }
        }
    
        return images;
    }        

    /**
     * @param texts
     * @return
     */
    private List<Range<Float>> getColumnRanges(Collection<TextPosition> texts) {
        TrapRangeBuilder rangesBuilder = new TrapRangeBuilder();

        /*
         * for (TextPosition ep : texts) { stdout.println("X:" + ep.getX() + "  Xwid: "
         * + ep.getWidth() + "  Y:" + ep.getY() + "  Yhgt:" + ep.getHeight() + "  Str: "
         * + ep.getUnicode()); stdout.println("endx: " + ep.getEndX() + " xscale:" +
         * ep.getXScale()); // stdout.println(ep.toString()); }
         */

        for (TextPosition text : texts) {
            // Range<Float> range = Range.closed((int) text.getX(), (int) (text.getX() +
            // text.getWidth()));
            Range<Float> range = Range.closed(text.getX(), text.getX() + text.getWidth());
            rangesBuilder.addRange(range);
        }
        return rangesBuilder.build();
    }

    // --------------------------------------------------------------------------
    // Implement N Override
    // --------------------------------------------------------------------------
    // Utils
    /**
     * Texts in tableContent have been ordered by .getY() ASC
     *
     * @param pageIdx
     * @param tableContent
     * @param rowTrapRanges
     * @param columnTrapRanges
     * @return
     */
    private Table buildTable(int pageIdx, List<TextPosition> tableContent, List<Range<Float>> rowTrapRanges,
            List<Range<Float>> columnTrapRanges) {

        Table retVal = new Table(pageIdx, columnTrapRanges.size());
        int idx = 0;
        int rowIdx = 0;
        List<TextPosition> rowContent = new ArrayList<>();
        while (idx < tableContent.size()) {
            TextPosition textPosition = tableContent.get(idx);
            Range<Float> rowTrapRange = rowTrapRanges.get(rowIdx);
            Range<Float> textRange = Range.closed(textPosition.getY(), textPosition.getY() + textPosition.getHeight());
            if (rowTrapRange.encloses(textRange)) {
                rowContent.add(textPosition);
                idx++;
            } else {
                TableRow row = buildRow(rowIdx, rowContent, columnTrapRanges);
                retVal.getRows().add(row);
                // next row: clear rowContent
                rowContent.clear();
                rowIdx++;
            }
        }
        // last row
        if (!rowContent.isEmpty() && rowIdx < rowTrapRanges.size()) {
            TableRow row = buildRow(rowIdx, rowContent, columnTrapRanges);
            retVal.getRows().add(row);
        }
        // return
        return retVal;
    }

    /**
     *
     * @param rowIdx
     * @param rowContent
     * @param columnTrapRanges
     * @return
     */
    private TableRow buildRow(int rowIdx, List<TextPosition> rowContent, List<Range<Float>> columnTrapRanges) {
        TableRow retVal = new TableRow(rowIdx);
        // Sort rowContent
        Collections.sort(rowContent, new Comparator<TextPosition>() {
            @Override
            public int compare(TextPosition o1, TextPosition o2) {
                int retVal = 0;
                if (o1.getX() < o2.getX()) {
                    retVal = -1;
                } else if (o1.getX() > o2.getX()) {
                    retVal = 1;
                }
                return retVal;
            }
        });
        int idx = 0;
        int columnIdx = 0;
        List<TextPosition> cellContent = new ArrayList<>();
        while (idx < rowContent.size()) {
            TextPosition textPosition = rowContent.get(idx);
            Range<Float> columnTrapRange = columnTrapRanges.get(columnIdx);
            Range<Float> textRange = Range.closed(textPosition.getX(), 
                                textPosition.getX() + textPosition.getWidth());
            if (columnTrapRange.encloses(textRange)) {
                cellContent.add(textPosition);
                idx++;
            } else {
                TableCell cell = buildCell(columnIdx, cellContent);
                retVal.getCells().add(cell);
                // next column: clear cell content
                cellContent.clear();
                columnIdx++;
            }
        }
        if (!cellContent.isEmpty() && columnIdx < columnTrapRanges.size()) {
            TableCell cell = buildCell(columnIdx, cellContent);
            retVal.getCells().add(cell);
        }
        // return
        return retVal;
    }

    private TableCell buildCell(int columnIdx, List<TextPosition> cellContent) {
        Collections.sort(cellContent, new Comparator<TextPosition>() {
            @Override
            public int compare(TextPosition o1, TextPosition o2) {
                int retVal = 0;
                if (o1.getX() < o2.getX()) {
                    retVal = -1;
                } else if (o1.getX() > o2.getX()) {
                    retVal = 1;
                }
                return retVal;
            }
        });
        // String cellContentString = Joiner.on("").join(cellContent.stream().map(e ->
        // e.getCharacter()).iterator());
        StringBuilder cellContentBuilder = new StringBuilder();
        for (TextPosition textPosition : cellContent) {
            cellContentBuilder.append(textPosition.getUnicode());
        }
        String cellContentString = cellContentBuilder.toString();
        return new TableCell(columnIdx, cellContentString);
    }
}

class PageRowColumnsProcessor {

    private final List<TextPosition> pageContent;
    private final List<Range<Float>> pageRowTrapRanges;
    private final Logger logger = LoggerFactory.getLogger(PageRowColumnsProcessor.class);

    public PageRowColumnsProcessor(List<TextPosition> tableContent, List<Range<Float>> rowTrapRanges) {
        this.pageContent = tableContent;
        this.pageRowTrapRanges = rowTrapRanges;
    }

    public List<Pair<Integer, Integer>> getTableRows() {

        Map<Integer, List<Range<Float>>> rowColumns = getRowColumnRanges();

        List<Pair<Integer, Integer>> retVal = new ArrayList();

        int lRowCount = rowColumns.keySet().size();
        logger.debug("Total rows:" + lRowCount);
        int startRow = 0;
        boolean inTable = false;
        List<Range<Float>> mergedColumns = new ArrayList<Range<Float>>();
        for (int idx = 0; idx < lRowCount - 1; idx++) {
            List<Range<Float>> columns = rowColumns.get(idx);
            List<Range<Float>> nextRowColumns = rowColumns.get(idx + 1);

            if (inTable &&  
                isAligned(mergedColumns, nextRowColumns, mergedColumns)
                || !inTable && isAligned(columns, nextRowColumns, mergedColumns)) {
                inTable = true;
                continue;
            }

            if (startRow == idx)
                startRow = idx + 1;
            else {
                retVal.add(new Pair<>(startRow, idx));
                startRow = idx + 1;
                inTable = false;
            }
        }
        if (inTable) {
            retVal.add(new Pair<>(startRow, lRowCount-1));
        }

        for (Pair<Integer, Integer> pair : retVal) {
            logger.debug("start row: " + pair.getKey() + " end row:" + pair.getValue());
        }

        return retVal;
    }

    private boolean isAligned(List<Range<Float>> firstRow, List<Range<Float>> secondRow,
                        List<Range<Float>> mergedRow) {

        int bigcolcnt = (firstRow.size() > secondRow.size()) ? firstRow.size() : secondRow.size();

        TrapRangeBuilder rangesBuilder = new TrapRangeBuilder();
        rangesBuilder.addRangeList(firstRow);
        rangesBuilder.addRangeList(secondRow);
        List<Range<Float>> mergedColumns = rangesBuilder.build();
        mergedRow.clear();
        mergedRow.addAll(mergedColumns);
        boolean retVal = mergedColumns.size() == bigcolcnt;
        return retVal;
    }

    /**
     * @param texts
     * @return
     */
    private Map<Integer, List<Range<Float>>> getRowColumnRanges() {
        Map<Integer, List<Range<Float>>> retVal = new HashMap<Integer, List<Range<Float>>>();
        for (int idx = 0; idx < this.pageRowTrapRanges.size(); idx++) {
            Range<Float> row = this.pageRowTrapRanges.get(idx);
            TrapRangeBuilder rangesBuilder = new TrapRangeBuilder();

            /*
             * for (TextPosition ep : texts) { stdout.println("X:" + ep.getX() + "  Xwid: "
             * + ep.getWidth() + "  Y:" + ep.getY() + "  Yhgt:" + ep.getHeight() + "  Str: "
             * + ep.getUnicode()); stdout.println("endx: " + ep.getEndX() + " xscale:" +
             * ep.getXScale()); // stdout.println(ep.toString()); }
             */

            for (TextPosition text : this.pageContent) {
                Range<Float> rangeY = Range.closed(text.getY(), text.getY() + text.getHeight());
                if (row.encloses(rangeY)) {
                    Range<Float> rangeX = Range.closed(text.getX(), text.getX() + text.getWidth());
                    rangesBuilder.addRange(rangeX);
                }
            }
            retVal.put(idx, rangesBuilder.build());
        }

        return retVal;
    }

}

class PageProcessor {
    private PDDocument document;
    private final Multimap<Integer, Integer> pageNExceptedLinesMap;

    public PageProcessor(PDDocument document, Multimap<Integer, Integer> pageNExceptedLinesMap) {
        this.document = document;
        this.pageNExceptedLinesMap = pageNExceptedLinesMap;
    }

    public void processPage(int pageId, Multimap<Integer, Range<Float>> pageIdNLineRangesMap,
            Multimap<Integer, TextPosition> pageIdNTextsMap) throws IOException {

        List<TextPosition> texts = extractTextPositionsInPage(pageId);// sorted by .getY() ASC
        // extract line ranges
        List<Range<Float>> lineRanges = getLineRangesWithoutExceptedLines(pageId, texts);
        // extract column ranges

        PageRowColumnsProcessor pagerowcolumns = new PageRowColumnsProcessor(texts,
                        lineRanges);
        List<Pair<Integer, Integer>> tablepos = pagerowcolumns.getTableRows();
        List<Range<Float>> detectedLineRanges = applyTablePos(lineRanges, tablepos);

        //List<TextPosition> textsByLineRanges = pickTextsByLineRanges(lineRanges, texts);
        List<TextPosition> textsByLineRanges = pickTextsByLineRanges(detectedLineRanges, texts);

        pageIdNLineRangesMap.putAll(pageId, detectedLineRanges);
        pageIdNTextsMap.putAll(pageId, textsByLineRanges);
    }

    private List<TextPosition> extractTextPositionsInPage(int pageId) throws IOException {
        TextPositionExtractor extractor = new TextPositionExtractor(document, pageId);
        return extractor.extract();
    }

    List<Range<Float>> applyTablePos(List<Range<Float>> lineRanges, 
                            List<Pair<Integer, Integer>> tablepos) {

        List<Range<Float>> retVal = new ArrayList<Range<Float>>();
        for (Pair<Integer, Integer> pair: tablepos) {
            int start = pair.getKey();
            int end = pair.getValue();
            for(int idx = start; idx <= end; idx++) {
                retVal.add(lineRanges.get(idx));
            }
        }
        return retVal;
    }

    /**
     *
     * Remove all texts in excepted lines
     *
     * TexPositions are sorted by .getY() ASC
     *
     * @param lineRanges
     * @param textPositions
     * @return
     */
    private List<TextPosition> pickTextsByLineRanges(List<Range<Float>> lineRanges, List<TextPosition> textPositions) {
        // textPosition is sorted by it Y value asc.
        List<TextPosition> retVal = new ArrayList<>();
        int idxText = 0;
        int lineIdx = 0;
        while (idxText < textPositions.size() && lineIdx < lineRanges.size()) {

            TextPosition textPosition = textPositions.get(idxText);

            Range<Float> textYRange = Range.closed(textPosition.getY(), textPosition.getY() + textPosition.getHeight());

            Range<Float> lineYRange = lineRanges.get(lineIdx);
            if (lineYRange.encloses(textYRange)) {
                retVal.add(textPosition);
                idxText++;
            } else if (lineYRange.upperEndpoint() < textYRange.lowerEndpoint()) {
                lineIdx++;
            } else {
                // not in chosen lines. Skip this textPosition.
                idxText++;
            }
        }
        //
        return retVal;
    }

    private List<Range<Float>> getLineRangesWithoutExceptedLines(int pageId, List<TextPosition> pageContent) {
        TrapRangeBuilder lineTrapRangeBuilder = new TrapRangeBuilder();
        for (TextPosition textPosition : pageContent) {
            Range<Float> lineRange = Range.closed(textPosition.getY(), textPosition.getY() + textPosition.getHeight());
            // add to builder
            lineTrapRangeBuilder.addRange(lineRange);
        }
        List<Range<Float>> lineTrapRanges = lineTrapRangeBuilder.build();
        List<Range<Float>> retVal = removeExceptedLines(pageId, lineTrapRanges);
        return retVal;
    }

    private List<Range<Float>> removeExceptedLines(int pageIdx, List<Range<Float>> lineTrapRanges) {
        List<Range<Float>> retVal = new ArrayList<>();
        for (int lineIdx = 0; lineIdx < lineTrapRanges.size(); lineIdx++) {
            boolean isExceptedLine = isExceptedLine(pageIdx, lineIdx)
                    || isExceptedLine(pageIdx, lineIdx - lineTrapRanges.size());
            if (!isExceptedLine) {
                retVal.add(lineTrapRanges.get(lineIdx));
            }
        }
        return retVal;
    }

    private boolean isExceptedLine(int pageIdx, int lineIdx) {
        boolean retVal = this.pageNExceptedLinesMap.containsEntry(pageIdx, lineIdx)
                || this.pageNExceptedLinesMap.containsEntry(-1, lineIdx);
        return retVal;
    }
}





// --------------------------------------------------------------------------
// Inner class
class TextPositionExtractor extends PDFTextStripper {

    private final List<TextPosition> textPositions = new ArrayList<>();
    private final int pageId;
    private final Logger logger = LoggerFactory.getLogger(TextPositionExtractor.class);

    TextPositionExtractor(PDDocument document, int pageId) throws IOException {
        super();
        super.setSortByPosition(true);
        super.document = document;
        this.pageId = pageId;
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        this.textPositions.addAll(textPositions);
    }

    /**
     * and order by textPosition.getY() ASC
     *
     * @return
     * @throws IOException
     */
    public List<TextPosition> extract() throws IOException {
        this.stripPage(pageId);

        // sort
        Collections.sort(textPositions, new Comparator<TextPosition>() {
            @Override
            public int compare(TextPosition o1, TextPosition o2) {
                int retVal = 0;
                if (o1.getY() < o2.getY()) {
                    retVal = -1;
                } else if (o1.getY() > o2.getY()) {
                    retVal = 1;
                }
                return retVal;

            }
        });
        return this.textPositions;
    }

    private void stripPage(int pageId) throws IOException {
        this.setStartPage(pageId + 1);
        this.setEndPage(pageId + 1);
        try (Writer writer = new OutputStreamWriter(new ByteArrayOutputStream())) {
            writeText(document, writer);
            // the above function shall call the overwritten writeString(),
            // so that we can get all the TextPosition in this page.
        }
    }

}


