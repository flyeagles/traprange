/**
 * Copyright (C) 2015, GIAYBAC
 *
 * Released under the MIT license
 */
package com.giaybac.traprange;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author THOQ LUONG Mar 19, 2015 10:43:22 PM
 */
public class TrapRangeBuilder {

    //--------------------------------------------------------------------------
    //  Members
    private final Logger logger = LoggerFactory.getLogger(TrapRangeBuilder.class);
    private final List<Range<Float>> ranges = new ArrayList<>();

    //--------------------------------------------------------------------------
    //  Initialization and releasation
    //--------------------------------------------------------------------------
    //  Getter N Setter
    //--------------------------------------------------------------------------
    //  Method binding
    public TrapRangeBuilder addRange(Range<Float> range) {
        ranges.add(range);
        return this;
    }

    public TrapRangeBuilder addRangeList(List<Range<Float>> ranges) {
        for (Range<Float> range: ranges) {
            addRange(range);
        }
        logger.debug("ranges: " + ranges.size());
        return this;
    }

    /**
     * The result will be ordered by lowerEndpoint ASC
     *
     * @return
     */
    public List<Range<Float>> build() {
        List<Range<Float>> retVal = new ArrayList<>();

        //order range by lower Bound
        Collections.sort(ranges, new Comparator<Range>() {
            @Override
            public int compare(Range o1, Range o2) {
                return o1.lowerEndpoint().compareTo(o2.lowerEndpoint());
            }
        });

        for (Range<Float> range : ranges) {
            if (retVal.isEmpty()) {
                retVal.add(range);
                continue;
            } 

            Range<Float> lastRange = retVal.get(retVal.size() - 1);
            if (lastRange.isConnected(range)) {
                Range newLastRange = lastRange.span(range);
                retVal.set(retVal.size() - 1, newLastRange);
            } else {
                retVal.add(range);
            }
        }
        //debug
        logger.debug("Found " + retVal.size() + " trap-range(s) from " + ranges.size());
        //return
        return retVal;
    }

    //--------------------------------------------------------------------------
    //  Implement N Override
    //--------------------------------------------------------------------------
    //  Utils
    //--------------------------------------------------------------------------
    //  Inner class
}
