package com.contentgrid.appserver.application.model.openapi.model;

import java.util.Comparator;
import lombok.experimental.UtilityClass;

@UtilityClass
class Comparators {
    public static final Comparator<String> UNDERSCORE_LAST = (s1, s2) -> {
        var s1Underscore = s1.charAt(0) == '_'?1:0;
        var s2Underscore = s2.charAt(0) == '_'?1:0;

        if(s1Underscore == s2Underscore) {
            return s1.compareTo(s2);
        }

        return s1Underscore - s2Underscore;
    };

}
