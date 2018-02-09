package org.hadatac.console.controllers.restapi;

import java.util.List;

import org.hadatac.utils.ApiUtil;
import org.hadatac.entity.pojo.Study;
import org.hadatac.entity.pojo.DataAcquisitionSchema;
import org.hadatac.entity.pojo.DataAcquisitionSchemaAttribute;
import org.hadatac.utils.State;
import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import be.objectify.deadbolt.java.actions.SubjectPresent;
import org.hadatac.console.controllers.AuthApplication;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;
import play.mvc.Controller;
import play.libs.Json;

public class RestApi extends Controller {

    //@Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result getStudies(){
        ObjectMapper mapper = new ObjectMapper();
        List<Study> theStudies = Study.find();
        System.out.println("[RestApi] found " + theStudies.size() + " things");
        if(theStudies.size() == 0){
            return notFound(ApiUtil.createResponse("No studies found", false));
        } else {
            JsonNode jsonObject = mapper.convertValue(theStudies, JsonNode.class);
            return ok(ApiUtil.createResponse(jsonObject, true));
        }
    }// /getStudies()

    //@Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    // test with http%3A%2F%2Fhadatac.org%2Fkb%2Fhbgd%23STD-CPP4
    public Result getStudy(String studyUri){
        ObjectMapper mapper = new ObjectMapper();
        Study result = Study.find(studyUri);
        System.out.println("[RestAPI] type: " + result.getType());
        if(result == null || result.getType() == null || result.getType() == ""){
            return notFound(ApiUtil.createResponse("Study with name/ID " + studyUri + " not found", false));
        } else {
            JsonNode jsonObject = mapper.convertValue(result, JsonNode.class);
            return ok(ApiUtil.createResponse(jsonObject, true));
        }
    }// /getStudy()

    // test with https%3A%2F%2Fhbgd.tw.rpi.edu%2Fns%2FSUBJID
    // or https%3A%2F%2Fhbgd.tw.rpi.edu%2Fns%2FHAZ
    //@Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result getVariable(String variableAttribute){
        ObjectMapper mapper = new ObjectMapper();
        List<DataAcquisitionSchemaAttribute> result = DataAcquisitionSchemaAttribute.findByAttribute(variableAttribute);
        //System.out.println("[RestAPI] attribute: " + result.getAttribute());
        if(result.size() == 0){
            return notFound(ApiUtil.createResponse("Variable with name/ID " + variableAttribute + " not found", false));
        } else {
            JsonNode jsonObject = mapper.convertValue(result, JsonNode.class);
            return ok(ApiUtil.createResponse(jsonObject, true));
        }
    }// /getVariable()

    /*
    // TODO: finish this
    //       we need to go study -> data acqusition(s) -> data acqusition schema(s) -> data acquisition schema attributes
    //@Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result getVariablesByStudy(String studyUri){
        ObjectMapper mapper = new ObjectMapper();
        Study theStudy = Study.findByName(studyName);
        
       	//public static List<DataAcquisitionSchemaAttribute> findBySchema (String schemaUri) {
    }// /getVariablesByStudy
    */

}// /RestApi
