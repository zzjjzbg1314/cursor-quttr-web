package com.example.cursorquitterweb.musicmv.dto;
import javax.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateSlotReconcileContractTest {
 @Test void explicitEmptySlotsAreValidButMissingAndInvalidSlotsRemainRejected(){
  try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()) {
   Validator validator=factory.getValidator();
   TemplateSlotReconcileRequest request=new TemplateSlotReconcileRequest();
   request.setSourceNodeId("node");request.setSourceLocalKey("immutable-source");
   assertFalse(validator.validate(request).isEmpty());
   request.setSlots(Collections.emptyList());assertTrue(validator.validate(request).isEmpty());
   request.setSlots(Collections.singletonList(new TemplatePromotionRequest.Slot()));
   assertFalse(validator.validate(request).isEmpty());
   request.setSlots(Collections.nCopies(201,new TemplatePromotionRequest.Slot()));
   assertFalse(validator.validateProperty(request,"slots").isEmpty());
   request.setSlots(null);assertFalse(validator.validate(request).isEmpty());
  }
 }
}
