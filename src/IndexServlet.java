
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.utils.URIBuilder;
import org.rdfhdt.hdt.dictionary.Dictionary;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.exceptions.NotFoundException;
import org.rdfhdt.hdt.hdt.*;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.IteratorTripleString;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.triples.TripleString;
import org.rdfhdt.hdtjena.NodeDictionary;

import com.google.appengine.api.blobstore.*;
import com.google.appengine.api.datastore.*;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Servlet implementation class IndexServlet
 */
public class IndexServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
    public HDT hdt = null;   

	public void init() throws ServletException
    {
		try {
			hdt = loadHDT();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} catch(IndexOutOfBoundsException e) {
		
		}
    }


	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String s = request.getParameter("subject");
		String p = request.getParameter("predicate");
		String o = request.getParameter("object");
		
		long TRIPLESPERPAGE = 100;
		
		final long page = Math.max(1, parseAsInteger(request.getParameter("page")));
		final long limit = TRIPLESPERPAGE, 
				offset = limit * (page - 1);
		

		try {
			IteratorTripleID triples = searchHDT(s, p, o);
			Model model = offsetLimit(triples, offset, limit);
			
			readControls(request, model, triples.estimatedNumResults(), TRIPLESPERPAGE, page);
			
			model.write(response.getOutputStream(), "TURTLE");

		} catch (NotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void readControls(HttpServletRequest request, Model model,
			long totalItems, long itemsPerPage, long currentPage) throws URISyntaxException {
		String uri = request.getScheme() + "://" +   // "http" + "://
	             request.getServerName();
		if(request.getServerPort() != 80) {
	        uri += ":" + request.getServerPort();
	    }
		uri += request.getRequestURI();
		String queryString = (request.getQueryString() != null ? "?" +
                request.getQueryString() : "");;
        String fullUri = uri + queryString;
        URIBuilder firstPage = new URIBuilder(fullUri); 
        firstPage.setParameter("page", "1");
        URIBuilder previousPage = new URIBuilder(fullUri);
        previousPage.setParameter("page", Long.toString(currentPage - 1));
        URIBuilder nextPage = new URIBuilder(fullUri);
        nextPage.setParameter("page", Long.toString(currentPage + 1));
        
        String controls = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.\n"
        		+ "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>.\n"
        		+ "@prefix owl: <http://www.w3.org/2002/07/owl#>.\n"
        		+ "@prefix skos: <http://www.w3.org/2004/02/skos/core#>.\n"
        		+ "@prefix xsd: <http://www.w3.org/2001/XMLSchema#>.\n"
        		+ "@prefix dc: <http://purl.org/dc/terms/>.\n"
        		+ "@prefix dcterms: <http://purl.org/dc/terms/>.\n"
        		+ "@prefix dc11: <http://purl.org/dc/elements/1.1/>.\n"
        		+ "@prefix foaf: <http://xmlns.com/foaf/0.1/>.\n"
        		+ "@prefix geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>.\n"
        		+ "@prefix dbpedia: <http://dbpedia.org/resource/>.\n"
        		+ "@prefix dbpedia-owl: <http://dbpedia.org/ontology/>.\n"
        		+ "@prefix dbpprop: <http://dbpedia.org/property/>.\n"
        		+ "@prefix hydra: <http://www.w3.org/ns/hydra/core#>.\n"
        		+ "@prefix void: <http://rdfs.org/ns/void#>.\n"
        		+ "@prefix : <"+uri+">.\n"
        		+ "\n"
        		+ "<"+uri+"#dataset> a void:Dataset, hydra:Collection;\n"
        		+ "    void:subset <"+fullUri+">;\n"
        		+ "    void:uriLookupEndpoint \""+uri+"{?subject,predicate,object}\";\n"
        		+ "    hydra:search _:triplePattern.\n"
        		+ "\n"
        		+ "_:triplePattern hydra:template \""+uri+"{?subject,predicate,object}\";\n"
        		+ "    hydra:mapping _:subject, _:predicate, _:object.\n"
        		+ "\n"
        		+ "_:subject hydra:variable \"subject\";\n"
        		+ "    hydra:property rdf:subject.\n"
        		+ "\n"
        		+ "_:predicate hydra:variable \"predicate\";\n"
        		+ "    hydra:property rdf:predicate.\n"
        		+ "\n"
        		+ "_:object hydra:variable \"object\";\n"
        		+ "    hydra:property rdf:object.\n"
        		+ "\n"
        		+ "<"+fullUri+"> a hydra:Collection, hydra:PagedCollection;\n"
        		+ "    hydra:totalItems \""+totalItems+"\"^^xsd:integer;\n"
        		+ "    dcterms:source <"+uri+"#dataset>;\n"
        		+ "    void:triples \""+totalItems+"\"^^xsd:integer;\n"
        		+ "    hydra:itemsPerPage \""+itemsPerPage+"\"^^xsd:integer;\n"
        		+ "    hydra:firstPage <"+firstPage +"> .";

        if(currentPage > 1)
        	controls += "<"+fullUri+"> hydra:previousPage <"+previousPage +"> .";

        if(totalItems > (itemsPerPage * currentPage))
        	controls += "<"+fullUri+"> hydra:nextPage <"+nextPage +"> .";

        StringReader reader = new StringReader(controls);
        model.read(reader, null, "TURTLE");
	}


	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	}
	
	private HDT loadHDT() throws IOException {
		Query query = new Query("__BlobInfo__");
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(query); 
        List<Entity> entList = pq.asList(FetchOptions.Builder.withLimit(1)); 
        
        // get the first file in blob store
        Entity entity = entList.get(0);
        String key = entity.getKey().getName();
        BlobKey blobKey = new BlobKey(key);
     
        
		BlobstoreInputStream blobStream = new BlobstoreInputStream(blobKey);
        BufferedInputStream bufferedIn = new BufferedInputStream(blobStream);
		
        // loadHDT loads it all in memory
        HDT hdt = HDTManager.loadHDT(bufferedIn, null);
        return hdt;
	}
	private IteratorTripleID searchHDT(String subject, String predicate, String object) throws IOException, NotFoundException {
		if(hdt == null) {
			hdt = loadHDT();
		}
		

		Dictionary dict = hdt.getDictionary();
		

		// look up the result from the HDT datasource
        final int subjectId = subject == null ? 0 : dict.stringToId(subject, TripleComponentRole.SUBJECT);
        final int predicateId = predicate == null ? 0 : dict.stringToId(predicate, TripleComponentRole.PREDICATE);
        final int objectId = object == null ? 0 : dict.stringToId(object, TripleComponentRole.OBJECT);
        
		final IteratorTripleID result = hdt.getTriples().search(new TripleID(subjectId, predicateId, objectId));

		return result;
	}
	private Model offsetLimit(IteratorTripleID triples, final long offset, final long limit) {
		if (offset < 0) throw new IndexOutOfBoundsException("offset");
		if (limit  < 1) throw new IllegalArgumentException("limit");
		
		final Model model = ModelFactory.createDefaultModel();
		Dictionary dict = hdt.getDictionary();
		NodeDictionary dictionary = new NodeDictionary(dict);
		
		// try to jump directly to the offset
		boolean atOffset;
		if (triples.canGoTo()) {
			try {
				triples.goTo(offset);
				atOffset = true;
			}
			// if the offset is outside the bounds, this page has no matches
			catch (IndexOutOfBoundsException exception) { atOffset = false; }
		}
		// if not possible, advance to the offset iteratively
		else {
			triples.goToStart();
			for (int i = 0; !(atOffset = i == offset) && triples.hasNext(); i++)
				triples.next();
		}

		// add `limit` triples to the result model
		if (atOffset) {
			for (int i = 0; i < limit && triples.hasNext(); i++) {
				TripleID tripleId = triples.next();
				Triple t = new Triple(
						dictionary.getNode(tripleId.getSubject(), TripleComponentRole.SUBJECT),
						dictionary.getNode(tripleId.getPredicate(), TripleComponentRole.PREDICATE),
						dictionary.getNode(tripleId.getObject(), TripleComponentRole.OBJECT)
					);
				model.add(model.asStatement(t));
			}
				
		}
		return model;

	}
	private int parseAsInteger(String value) {
		try { return Integer.parseInt(value); }
		catch (NumberFormatException ex) { return 0; }
	}


}
