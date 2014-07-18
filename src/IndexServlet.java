
import java.io.*;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
		
		long TRIPLESPERPAGE = 10;
		
		final long page = Math.max(1, parseAsInteger(request.getParameter("page")));
		final long limit = TRIPLESPERPAGE, 
				offset = limit * (page - 1);
		

		try {
			Model model = searchHDT(s, p, o, offset, limit);
			
			model.write(response.getOutputStream(), "N-TRIPLES");

			
		} catch (NotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	}
	
	private HDT loadHDT() throws IOException {
		Query query = new Query("__BlobInfo__");
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(query); 
        List<Entity> entList = pq.asList(FetchOptions.Builder.withLimit(1)); 
        
        // get the first file in blobstore
        Entity entity = entList.get(0);
        String key = entity.getKey().getName();
        BlobKey blobKey = new BlobKey(key);
        
		BlobstoreInputStream blobStream = new BlobstoreInputStream(blobKey);
        BufferedInputStream bufferedIn = new BufferedInputStream(blobStream);
		
        // loadHDT loads it all in memory
        HDT hdt = HDTManager.loadHDT(bufferedIn, null);
        return hdt;
	}
	private Model searchHDT(String subject, String predicate, String object, final long offset, final long limit) throws IOException, NotFoundException {
		if(hdt == null) {
			hdt = loadHDT();
		}
		if (offset < 0) throw new IndexOutOfBoundsException("offset");
		if (limit  < 1) throw new IllegalArgumentException("limit");

		Dictionary dict = hdt.getDictionary();
		NodeDictionary dictionary = new NodeDictionary(dict);
		

		// look up the result from the HDT datasource
        final int subjectId = subject == null ? 0 : dict.stringToId(subject, TripleComponentRole.SUBJECT);
        final int predicateId = predicate == null ? 0 : dict.stringToId(predicate, TripleComponentRole.PREDICATE);
        final int objectId = object == null ? 0 : dict.stringToId(object, TripleComponentRole.OBJECT);
        
		final IteratorTripleID result = hdt.getTriples().search(new TripleID(subjectId, predicateId, objectId));


		final Model triples = ModelFactory.createDefaultModel();

		// try to jump directly to the offset
		boolean atOffset;
		if (result.canGoTo()) {
			try {
				result.goTo(offset);
				atOffset = true;
			}
			// if the offset is outside the bounds, this page has no matches
			catch (IndexOutOfBoundsException exception) { atOffset = false; }
		}
		// if not possible, advance to the offset iteratively
		else {
			result.goToStart();
			for (int i = 0; !(atOffset = i == offset) && result.hasNext(); i++)
				result.next();
		}

		// add `limit` triples to the result model
		if (atOffset) {
			for (int i = 0; i < limit && result.hasNext(); i++) {
				TripleID tripleId = result.next();
				Triple t = new Triple(
						dictionary.getNode(tripleId.getSubject(), TripleComponentRole.SUBJECT),
						dictionary.getNode(tripleId.getPredicate(), TripleComponentRole.PREDICATE),
						dictionary.getNode(tripleId.getObject(), TripleComponentRole.OBJECT)
					);
				triples.add(triples.asStatement(t));
			}
				
		}
		return triples;

	}
	private int parseAsInteger(String value) {
		try { return Integer.parseInt(value); }
		catch (NumberFormatException ex) { return 0; }
	}


}
