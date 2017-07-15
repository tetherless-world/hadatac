package org.hadatac.entity.pojo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.hadatac.utils.Collections;
import org.hadatac.utils.NameSpaces;

import play.Play;

public class Attribute implements HADatAcClass, Comparable<Attribute> {
	private String uri;
	private String superUri;
	private String localName;
	private String label;
	
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getSuperUri() {
		return superUri;
	}
	public void setSuperUri(String superUri) {
		this.superUri = superUri;
	}
	public String getLocalName() {
		return localName;
	}
	public void setLocalName(String localName) {
		this.localName = localName;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	
	public static List<Attribute> find() {
	    List<Attribute> attributes = new ArrayList<Attribute>();
	    String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() +
		" SELECT ?uri WHERE { " +
		" ?uri rdfs:subClassOf* sio:Attribute . " + 
		"} ";
	    
	    //System.out.println("Query: " + queryString);
	    Query query = QueryFactory.create(queryString);
	    
	    QueryExecution qexec = QueryExecutionFactory.sparqlService(Collections.getCollectionsName(Collections.METADATA_SPARQL), query);
	    ResultSet results = qexec.execSelect();
	    ResultSetRewindable resultsrw = ResultSetFactory.copyResults(results);
	    qexec.close();
	    
	    while (resultsrw.hasNext()) {
		QuerySolution soln = resultsrw.next();
		Attribute attribute = find(soln.getResource("uri").getURI());
		attributes.add(attribute);
	    }			
	    
	    java.util.Collections.sort((List<Attribute>) attributes);
	    return attributes;
	    
	}

	public static Map<String,String> getMap() {
	    List<Attribute> list = find();
	    Map<String,String> map = new HashMap<String,String>();
	    for (Attribute att : list) 
		map.put(att.getUri(),att.getLabel());
	    return map;
	}

	public static Attribute find(String uri) {
	    Attribute attribute = null;
	    Model model;
	    Statement statement;
	    RDFNode object;
	    
	    String queryString = "DESCRIBE <" + uri + ">";
	    Query query = QueryFactory.create(queryString);
	    QueryExecution qexec = QueryExecutionFactory.sparqlService(Play.application().configuration().getString("hadatac.solr.triplestore") 
								       + Collections.METADATA_SPARQL, query);
	    model = qexec.execDescribe();
	    
	    attribute = new Attribute();
	    StmtIterator stmtIterator = model.listStatements();
	    
	    while (stmtIterator.hasNext()) {
		statement = stmtIterator.next();
		object = statement.getObject();
		if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#label")) {
		    attribute.setLabel(object.asLiteral().getString());
		} else if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#subClassOf")) {
		    attribute.setSuperUri(object.asResource().getURI());
		}
	    }
	    
	    attribute.setUri(uri);
	    attribute.setLocalName(uri.substring(uri.indexOf('#') + 1));
	    
	    //System.out.println(uri + " " + entity.getLocalName() + " " + entity.getSuperUri());
	    
	    return attribute;
	}
	
	@Override
	    public int compareTo(Attribute another) {
	    if (this.getLabel() != null && another.getLabel() != null) {
		   return this.getLabel().compareTo(another.getLabel());
	    }
	    return this.getLocalName().compareTo(another.getLocalName());
	}
	
    public static String getHierarchyJson() {
	String collection = "";
	String q = 
	    "SELECT ?id ?superId ?label ?comment WHERE { " + 
	    "   ?id rdfs:subClassOf* sio:Attribute . " + 
	    "   ?id rdfs:subClassOf ?superId .  " + 
	    "   OPTIONAL { ?id rdfs:label ?label . } " + 
	    "   OPTIONAL { ?id rdfs:comment ?comment . } " +
	    "}";
    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    	try {
	    String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + q;
	    Query query = QueryFactory.create(queryString);
	    QueryExecution qexec = QueryExecutionFactory.sparqlService(Collections.getCollectionsName(Collections.METADATA_SPARQL), query);
	    ResultSet results = qexec.execSelect();
	    ResultSetFormatter.outputAsJSON(outputStream, results);
	    qexec.close();
	    
	    return outputStream.toString("UTF-8");
    	} catch (Exception e) {
	    e.printStackTrace();
	}
    	return "";
    }
    
}
