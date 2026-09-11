package com.example.cursorquitterweb.musicmv.dto;
import javax.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplatePromotionSlotContractTest {
 @Test void explicitEmptyListIsDifferentFromMissingContract(){
  try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()){
   Validator v=f.getValidator();TemplatePromotionRequest r=new TemplatePromotionRequest();
   assertFalse(v.validateProperty(r,"slots").isEmpty());
   r.setSlots(Collections.emptyList());assertTrue(v.validateProperty(r,"slots").isEmpty());
   r.setSlots(null);assertFalse(v.validateProperty(r,"slots").isEmpty());
  }
 }
 @Test void existingSlotBoundRemainsEnforced(){
  try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()){
   TemplatePromotionRequest r=new TemplatePromotionRequest();r.setSlots(Collections.nCopies(200,new TemplatePromotionRequest.Slot()));
   assertTrue(f.getValidator().validateProperty(r,"slots").isEmpty());
   r.setSlots(Collections.nCopies(201,new TemplatePromotionRequest.Slot()));assertFalse(f.getValidator().validateProperty(r,"slots").isEmpty());
  }
 }
}
