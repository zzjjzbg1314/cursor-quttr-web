package com.example.cursorquitterweb.musicmv.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BrowserNativeEffectKeyframesTest {
    private Object frames(String property,String values,String time) throws Exception {
        return new ObjectMapper().readValue("[{\"id\":\"curve\",\"property_type\":\""+property+"\",\"keyframe_list\":[{\"time_offset\":"+time+",\"curveType\":\"Line\",\"values\":"+values+"}]}]",List.class);
    }
    @Test void declaredScalarParametersAreAccepted() throws Exception {
        BrowserNativeEffectKeyframes.validate(frames("KFTypeFilter","[0.5]","0"),Collections.emptySet());
        BrowserNativeEffectKeyframes.validate(frames("speed","[0.2]","1000000"),Collections.singleton("speed"));
    }
    @Test void unknownOrInvalidValuesRemainBlocked() throws Exception {
        assertThrows(IOException.class,()->BrowserNativeEffectKeyframes.validate(frames("other","[0.5]","0"),Collections.emptySet()));
        assertThrows(IOException.class,()->BrowserNativeEffectKeyframes.validate(frames("KFTypeFilter","[0,1]","0"),Collections.emptySet()));
        assertThrows(IOException.class,()->BrowserNativeEffectKeyframes.validate(frames("KFTypeFilter","[0.5]","-1"),Collections.emptySet()));
        assertThrows(IOException.class,()->BrowserNativeEffectKeyframes.validate(frames("KFTypeFilter","[0.5]","9007199254740992"),Collections.emptySet()));
    }
}
