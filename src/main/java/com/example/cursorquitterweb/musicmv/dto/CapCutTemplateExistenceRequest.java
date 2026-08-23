package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CapCutTemplateExistenceRequest {
    @NotNull @Size(min = 1, max = 500)
    private List<String> capcutTemplateIds = new ArrayList<String>();

    public List<String> getCapcutTemplateIds() { return capcutTemplateIds; }
    public void setCapcutTemplateIds(List<String> value) {
        capcutTemplateIds = value == null ? new ArrayList<String>() : value;
    }
}
