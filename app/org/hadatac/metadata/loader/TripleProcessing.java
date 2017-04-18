package org.hadatac.metadata.loader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.controllers.triplestore.UserManagement;
import org.hadatac.console.models.SysUser;
import org.hadatac.entity.pojo.DataAcquisition;
import org.hadatac.entity.pojo.Deployment;
import org.hadatac.entity.pojo.Measurement;
import org.hadatac.entity.pojo.TriggeringEvent;
import org.hadatac.metadata.loader.LabkeyDataHandler.PlainTriple;
import org.hadatac.metadata.model.SpreadsheetParsingResult;
import org.hadatac.utils.Feedback;
import org.hadatac.utils.NameSpaces;
import org.labkey.remoteapi.CommandException;

import play.mvc.Controller;

public class TripleProcessing {
	
	public static final String KB_FORMAT = "text/turtle";
	
	public static final String TTL_DIR = "tmp/ttl/";
	
	public static String printFileWithLineNumber(int mode, String filename) {
		String str = "";
		int lineNumber = 1;

        LineNumberReader reader = null;
        String line = null;

        try {
            reader = new LineNumberReader(new FileReader(filename));
            while ((line = reader.readLine()) != null) {
                str += Feedback.println(mode, lineNumber++ + " " + line);
            }
            reader.close();
        } catch (Exception e) {
        	e.printStackTrace();
        }
        return str;
	}
	
	public static SpreadsheetParsingResult generateTTL(int mode, 
			Map< String, List<PlainTriple> > sheet, List<String> predicates) {
		String shttl = "";
		String message = "";

		ValueCellProcessing cellProc = new ValueCellProcessing();
		
		// Prints all identified predicates as a turtle comment 
		shttl = shttl + "# properties: ";
		for (String pred : predicates) {
			cellProc.validateNameSpace(pred);
			shttl = shttl + "[" + pred + "] ";
		}
		shttl = shttl + "\n";
		
		int processedRows = 0;
		int processedTriples = 0;
		for (String uri : sheet.keySet()) {
			List<PlainTriple> row = sheet.get(uri);
			shttl = shttl + processTriplesOfRow(row, predicates);
			processedRows++;
			processedTriples += row.size();
		}
		
		System.out.println(String.format("%d rows processed!", processedRows));
		message += Feedback.println(mode, "processed " + processedRows + " row(s) " + "( " + processedTriples + " Triples ).");
		SpreadsheetParsingResult result = new SpreadsheetParsingResult(message, shttl);
		return result;
	}
	
	public static String processTriplesOfRow(List<PlainTriple> triples, List<String> predicates) {
		String clttl = "";
		
		boolean bListSubject = false;
		ValueCellProcessing cellProc = new ValueCellProcessing();
		Iterator<PlainTriple> iterTriple = triples.iterator();
		while(iterTriple.hasNext()){
			PlainTriple triple = iterTriple.next();
			String cellValue = triple.obj.trim();
			String predicate = triple.pred.trim();
			
			if(!bListSubject){
				clttl = clttl + cellProc.processSubjectValue(triple.sub.trim());
				bListSubject = true;
			}
			
			// cell has object value
			clttl = clttl + "   " + predicate + " ";
			if (cellProc.isObjectSet(cellValue)) {
				StringTokenizer st;
				if(cellValue.contains("&")){
					st = new StringTokenizer(cellValue, "&");
				}
				else{
					st = new StringTokenizer(cellValue, ",");
				}
				while (st.hasMoreTokens()) {
					clttl = clttl + cellProc.processObjectValue(st.nextToken().trim());
					if (st.hasMoreTokens()){
						clttl = clttl + ", ";
					}
				}
			}
			else{
				clttl = clttl + cellProc.processObjectValue(cellValue);
			}
			if(iterTriple.hasNext()){
				clttl = clttl + " ; \n";
			}
			else{
				clttl = clttl + " . \n\n";
			}
		}
				
		return clttl;
	}
	
    public static List<String> getLabKeyMetadataLists(String labkey_site, String user_name, 
    		String password, String path) throws CommandException {
    	
    	LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		try {
			List<String> queryNames = loader.getMetadataQueryNames(false);
			return queryNames;
		} catch (CommandException e) {
			if(e.getMessage().equals("Unauthorized")){
				throw e;
			}
		}
		return null;
	}
    
    public static List<String> getLabKeyInstanceDataLists(String labkey_site, String user_name, 
    		String password, String path) throws CommandException {
    	
    	LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		try {
			List<String> queryNames = loader.getInstanceDataQueryNames();
			return queryNames;
		} catch (CommandException e) {
			if(e.getMessage().equals("Unauthorized")){
				throw e;
			}
		}
		return null;
	}
    
    public static List<String> getLabKeyFolders(String labkey_site, String user_name, 
    		String password, String path) throws CommandException {
    	
    	LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		try {
			List<String> folders = loader.getSubfolders();
			return folders;
		} catch (CommandException e) {
			if(e.getMessage().equals("Unauthorized")){
				throw e;
			}
		}
		return null;
	}
    
    private static String loadTriples(
    		LabkeyDataHandler loader, 
    		List<String> list_names, 
    		Map< String, Map< String, List<PlainTriple> > > mapSheets, 
    		Map< String, List<String> > mapPreds) throws CommandException {
    	
    	String message = "";
		try {
			List<String> queryNames = null;
			if(list_names == null){
				queryNames = loader.getAllQueryNames();
			}
			else{
				queryNames = new LinkedList<String>();
				queryNames.addAll(list_names);
			}
			for(String query : queryNames){
				List<String> cols = loader.getColumnNames(query, false);
				if(loader.containsInstanceData(cols) || loader.containsMetaData(cols)){
					mapSheets.put(query, loader.selectRows(query, cols));
				}
				mapPreds.put(query, cols);
			}
			System.out.println("Data extraction finished...");
		} catch (CommandException e) {
			if(e.getMessage().equals("Unauthorized")){
				throw e;
			}
			else{
				return e.getMessage();
			}
		}
		
		return message;
    }
    
    public static Model importStudy(String labkey_site, String user_name, 
    		String password, String path, String studyUri) throws CommandException {
    	
		LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		Map< String, Map< String, List<PlainTriple> > > mapSheets = 
				new HashMap< String, Map< String, List<PlainTriple> > >();
		Map< String, List<String> > mapPreds = 
				new HashMap< String, List<String> >();
		
		loadTriples(loader, loader.getAllQueryNames(), mapSheets, mapPreds);
		String ttl = NameSpaces.getInstance().printTurtleNameSpaceList();
		for(String queryName : mapSheets.keySet()){
			Map< String, List<PlainTriple> > sheet = mapSheets.get(queryName);
			SpreadsheetParsingResult result = generateTTL(Feedback.WEB, sheet, mapPreds.get(queryName));
			ttl = ttl + "\n# concept: " + queryName + result.getTurtle() + "\n";
		}
		String fileName = "";
		try {
			fileName = TTL_DIR + "labkey.ttl";
			FileUtils.writeStringToFile(new File(fileName), ttl);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Model refModel = RDFDataMgr.loadModel(fileName);
		Model targetModel = ModelFactory.createDefaultModel();
		ValueCellProcessing cellProc = new ValueCellProcessing();
		Selector selector = new SimpleSelector(
				refModel.getResource(cellProc.replacePrefixEx(studyUri)), (Property)null, (RDFNode)null);
		StmtIterator iter = refModel.listStatements(selector);
		if (iter.hasNext()) {
			Resource studyNode = iter.nextStatement().getSubject();
			forwardTraverseGraph(studyNode, null, refModel, targetModel);
		}
		
		selector = new SimpleSelector(
				null, (Property)null, refModel.getResource(cellProc.replacePrefixEx(studyUri)));
		iter = refModel.listStatements(selector);
		if (iter.hasNext()) {
			RDFNode studyNode = iter.nextStatement().getObject();
			backwardTraverseGraph((Resource)studyNode, refModel, targetModel);
		}
		
		return targetModel;
    }
    
    private static void forwardTraverseGraph(Resource node, RDFNode ignoredNode, 
    		Model refModel, Model targetModel) {
    	StmtIterator iter = node.listProperties();
    	while (iter.hasNext()) {
    		Statement stmt = iter.nextStatement();
    		RDFNode object = stmt.getObject();
    		if (object.isResource() && object.equals(ignoredNode)) {
    			forwardTraverseGraph((Resource)object, null, refModel, targetModel);
    		}
    		targetModel.add(node, stmt.getPredicate(), object);
    	}
    }
    
    private static void backwardTraverseGraph(Resource node, Model refModel, Model targetModel) {
    	Selector selector = new SimpleSelector(null, (Property)null, node);
    	StmtIterator iter = refModel.listStatements(selector);
    	while (iter.hasNext()) {
    		Statement stmt = iter.nextStatement();
    		Resource subject = stmt.getSubject();
    		backwardTraverseGraph(subject, refModel, targetModel);
    		forwardTraverseGraph(subject, node, refModel, targetModel);
    		targetModel.add(subject, stmt.getPredicate(), node);
    	}
    }
    
    public static String importDataAcquisition(String labkey_site, String user_name, 
    		String password, String path, List<String> list_names) throws CommandException {
    	
    	final SysUser user = AuthApplication.getLocalUser(Controller.session());
		String ownerUri = UserManagement.getUriByEmail(user.getEmail());
		
    	String message = "";
    	LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		Map< String, Map< String, List<PlainTriple> > > mapSheets = 
				new HashMap< String, Map< String, List<PlainTriple> > >();
		Map< String, List<String> > mapPreds = 
				new HashMap< String, List<String> >();
		
		String ret = loadTriples(loader, list_names, mapSheets, mapPreds);
		
		if(!ret.equals("")){
			return (message + ret);
		}
		
		for(String queryName : mapSheets.keySet()){
			Map< String, List<PlainTriple> > sheet = mapSheets.get(queryName);
			for (String uri : sheet.keySet()) {
				System.out.println(String.format("Processing data acquisition %s", uri));
				
				ValueCellProcessing cellProc = new ValueCellProcessing();
				String dataAcquisitionUri = cellProc.convertToWholeURI(uri);
				DataAcquisition dataAcquisition = DataAcquisition.findByUri(dataAcquisitionUri);
				if (null == dataAcquisition) {
					dataAcquisition = new DataAcquisition();
					dataAcquisition.setUri(dataAcquisitionUri);
					dataAcquisition.setOwnerUri(ownerUri);
					dataAcquisition.setPermissionUri(ownerUri);
					dataAcquisition.setTriggeringEvent(TriggeringEvent.INITIAL_DEPLOYMENT);
					dataAcquisition.setNumberDataPoints(
							Measurement.getNumByDataAcquisition(dataAcquisition));
				}
				
				for (PlainTriple triple : sheet.get(uri)) {
					String cellValue = triple.obj.trim();
					String predicate = triple.pred.trim();
					
					if (predicate.equals("a")) {
						StringTokenizer st;
						if(cellValue.contains("&")){
							st = new StringTokenizer(cellValue, "&");
						}
						else{
							st = new StringTokenizer(cellValue, ",");
						}
						while (st.hasMoreTokens()) {
							dataAcquisition.addTypeUri(
									cellProc.convertToWholeURI(st.nextToken().trim()));
						}
					}
					else if (predicate.equals("prov:wasAssociatedWith")) {
						StringTokenizer st;
						if(cellValue.contains("&")){
							st = new StringTokenizer(cellValue, "&");
						}
						else{
							st = new StringTokenizer(cellValue, ",");
						}
						while (st.hasMoreTokens()) {
							dataAcquisition.addAssociatedUri(
									cellProc.convertToWholeURI(st.nextToken().trim()));
						}
					}
					else if (predicate.equals("rdfs:label")) {
						dataAcquisition.setLabel(cellValue);
					}
					else if (predicate.equals("rdfs:comment")) {
						dataAcquisition.setComment(cellValue);
					}
					else if (predicate.equals("prov:startedAtTime")) {
						dataAcquisition.setStartedAt(cellValue);
					}
					else if (predicate.equals("prov:endedAtTime")) {
						dataAcquisition.setEndedAt(cellValue);
					}
					else if (predicate.equals("prov:used")) {
						dataAcquisition.setParameter(cellValue);
					}
					else if (predicate.equals("hasco:isDataAcquisitionOf")) {
						dataAcquisition.setStudyUri(cellProc.convertToWholeURI(cellValue));
					}
					else if (predicate.equals("hasco:hasTriggeringEvent")) {
						dataAcquisition.setTriggeringEvent(dataAcquisition.getTriggeringEventByName(cellValue));
					}
					else if (predicate.equals("hasco:hasMethod")) {
						dataAcquisition.setMethodUri(cellProc.convertToWholeURI(cellValue));
					}
					else if (predicate.equals("hasco:hasSchema")) {
						dataAcquisition.setSchemaUri(cellProc.convertToWholeURI(cellValue));
					}
					else if (predicate.equals("hasneto:hasDeployment")) {
						String deployment_uri = cellProc.convertToWholeURI(cellValue);
						dataAcquisition.setDeploymentUri(deployment_uri);
						
						Deployment deployment = Deployment.find(deployment_uri);
						if (deployment != null) {
							dataAcquisition.setPlatformUri(deployment.getPlatform().getUri());
							dataAcquisition.setInstrumentUri(deployment.getInstrument().getUri());
							dataAcquisition.setPlatformName(deployment.getPlatform().getLabel());
							dataAcquisition.setInstrumentModel(deployment.getInstrument().getLabel());
							dataAcquisition.setStartedAtXsdWithMillis(deployment.getStartedAt());
						}
					}
				}
				
				if (dataAcquisition.getStartedAt() == null || dataAcquisition.getStartedAt().isEmpty() 
						|| dataAcquisition.getDeploymentUri() == null || dataAcquisition.getDeploymentUri().isEmpty()
						|| dataAcquisition.getSchemaUri() == null || dataAcquisition.getSchemaUri().isEmpty()) {
					continue;
				}
				
				dataAcquisition.save();
				System.out.println("Successfully saved " + dataAcquisition.getUri() + " in Solr");
			}
		}
		
		return message;
    }

    public static String generateTTL(int mode, String oper, RDFContext rdf, String labkey_site, 
    		String user_name, String password, String path, List<String> list_names) throws CommandException {

		String message = "";
		if (oper.equals("load")) {
			message += Feedback.println(mode, "   Triples before loading from LABKEY: " + rdf.totalTriples());
			message += Feedback.println(mode, " ");
		}
		
		LabkeyDataHandler loader = new LabkeyDataHandler(labkey_site, user_name, password, path);
		Map< String, Map< String, List<PlainTriple> > > mapSheets = 
				new HashMap< String, Map< String, List<PlainTriple> > >();
		Map< String, List<String> > mapPreds = 
				new HashMap< String, List<String> >();
		
		String ret = loadTriples(loader, list_names, mapSheets, mapPreds);
		if(!ret.equals("")){
			return (message + ret);
		}
		
		message += Feedback.println(mode, "   Parsing triples from LABKEY " );
		message += Feedback.println(mode, " ");
		
		String ttl = NameSpaces.getInstance().printTurtleNameSpaceList();
		for(String queryName : mapSheets.keySet()){
			Map< String, List<PlainTriple> > sheet = mapSheets.get(queryName);
			message += Feedback.print(mode, "   Processing sheet " + queryName + "  ()   ");
			for (int i = queryName.length(); i < 25; i++) {
				message += Feedback.print(mode, ".");
			}
			SpreadsheetParsingResult result = generateTTL(mode, sheet, mapPreds.get(queryName));
			ttl = ttl + "\n# concept: " + queryName + result.getTurtle() + "\n";
			message += result.getMessage();
		}

		String fileName = "";
		try {
			fileName = TTL_DIR + "labkey.ttl";
			FileUtils.writeStringToFile(new File(fileName), ttl);
		} catch (IOException e) {
			message += e.getMessage();
			return message;
		}
		
		String listing = "";
		try {
			listing = URLEncoder.encode(SpreadsheetProcessing.printFileWithLineNumber(mode, fileName), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		};

		System.out.println("");
		message += Feedback.println(mode, " ");
		message += Feedback.println(mode, "   Generated " + fileName + " and stored locally.");
		try {
			RDFDataMgr.loadModel(fileName);
			message += Feedback.println(mode, " ");
			message += Feedback.print(mode, "SUCCESS parsing the document!");
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, "==== TURTLE (TTL) CODE GENERATED FROM LABKEY ====");
			message += listing;
		} catch (Exception e) {
			message += Feedback.println(mode, " ");
			message += Feedback.print(mode, "ERROR parsing the document!");
			message += Feedback.println(mode, " ");
			message += e.getMessage();
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, "==== TURTLE (TTL) CODE GENERATED FROM LABKEY ====");
			message += listing;
			return message;
		}

		if (oper.equals("load")) {
		    message += Feedback.print(mode, "   Uploading generated file.");
		    rdf.loadLocalFile(mode, fileName, KB_FORMAT);
		    message += Feedback.println(mode, "");
		    message += Feedback.println(mode, " ");
		    message += Feedback.println(mode, "   Triples after [loading from LABKEY]: " + rdf.totalTriples());
		}

	    return message;
	}
    
    public static String processTTL(int mode, String oper, RDFContext rdf, String fileName) {
		String message = "";
		if (oper.equals("load")) {
		   message += Feedback.println(mode, "   Triples before loading from LABKEY: " + rdf.totalTriples());
		   message += Feedback.println(mode, " ");
		}
		
		String listing = "";
		try {
			listing = URLEncoder.encode(SpreadsheetProcessing.printFileWithLineNumber(mode, fileName), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		};

		message += Feedback.println(mode, " ");
		message += Feedback.println(mode, "   Generated " + fileName + " and stored locally.");
		try {
			RDFDataMgr.loadModel(fileName);
			message += Feedback.println(mode, " ");
			message += Feedback.print(mode, "SUCCESS parsing the document!");
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, "==== TURTLE (TTL) FILE CONTENT ====");
			message += listing;
		} catch (Exception e) {
			message += Feedback.println(mode, " ");
			message += Feedback.print(mode, "ERROR parsing the document!");
			message += Feedback.println(mode, " ");
			message += e.getMessage();
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, " ");
			message += Feedback.println(mode, "==== TURTLE (TTL) FILE CONTENT ====");
			message += listing;
			return message;
		}

	    message += Feedback.print(mode, "   Uploading generated file.");
	    rdf.loadLocalFile(mode, fileName, KB_FORMAT);
	    message += Feedback.println(mode, "");
	    message += Feedback.println(mode, " ");
	    message += Feedback.println(mode, "   Triples after [loading]: " + rdf.totalTriples());
	    return message;
	}
}