importPackage(java.lang);
importPackage(java.util);
importPackage(java.nio.charset);
importPackage(org.apache.jena.riot);
importPackage(org.apache.jena.riot.system);
importPackage(com.hp.hpl.jena.rdf.model);
importPackage(com.hp.hpl.jena.query);
importPackage(com.google.appengine.api.datastore);
importPackage(org.apache.jena.atlas.web);
importPackage(org.apache.http.client.utils);
importPackage(com.google.appengine.api.taskqueue);
importPackage(com.google.appengine.api.blobstore);

// hdt
importPackage(org.rdfhdt.hdt.hdt);
importPackage(org.rdfhdt.hdtjena);

var apejs = require("apejs.js");
var select = require('select.js');

apejs.urls = {
    "/": {
        get: function(request, response, query) {
            var html = render("skins/index.html");
            return print(response).html(html);
        },
    },
    '/upload': {
        get: function(request, response) {
            var blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
            var uploadUrl = blobstoreService.createUploadUrl("/upload")

            var html = render("skins/upload.html");
            html = html.replace('<%=%>', uploadUrl);

            return print(response).html(html);
            
        },
        post: function(request, response) {
            var blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
            var blobs = blobstoreService.getUploadedBlobs(request);
            var blobKey = blobs.get("myFile");

            var servletContext = getServletConfig().getServletContext();

            var blobStream = BlobstoreInputStream(blobKey);
            var bufferedIn = new BufferedInputStream(blobStream);

            var hdt = HDTManager.loadHDT(bufferedIn, null);
            var graph = new HDTGraph(hdt);
            var model = ModelFactory.createModelForGraph(graph);

            System.out.println('from file');
            servletContext.setAttribute('modelCache', model);
            print(response).text(blobKey.getKeyString());
        }

    },
    '/sparql': {
        get: function(request, response) {

            var servletContext = getServletConfig().getServletContext();
            var modelCache = servletContext.getAttribute('modelCache');
            if(modelCache) {
                // XXX comment this if you don't wanna use cache
                System.out.println('from cache');
                done(modelCache);
                return;
            }

            var query = new com.google.appengine.api.datastore.Query("__BlobInfo__"); 
            var datastore = DatastoreServiceFactory.getDatastoreService(); 
            var pq = datastore.prepare(query); 
            var entList = pq.asList(FetchOptions.Builder.withLimit(1)); 
            // get the first file in blobstore
            var key = entList.get(0).getKey().getName();
            var blobKey = new BlobKey(key);

            var servletContext = getServletConfig().getServletContext();

            var blobStream = BlobstoreInputStream(blobKey);
            var bufferedIn = new BufferedInputStream(blobStream);

            var hdt = HDTManager.loadHDT(bufferedIn, null);
            var graph = new HDTGraph(hdt);
            var model = ModelFactory.createModelForGraph(graph);

            System.out.println('from file');
            servletContext.setAttribute('modelCache', model);

            done(model);


            // DONE
            function done(m) {
                var queryString = request.getParameter('query');
                if(!queryString) queryString = 'SELECT * WHERE { ?s ?p ?o . } LIMIT 10';
                var query = QueryFactory.create(queryString);

                // Execute the query and obtain results
                var qe = QueryExecutionFactory.create(query, m);
                var results = qe.execSelect();

                var arr = [];
                while(results.hasNext()) {
                    var querySolution = results.next();
                    var obj = {};

                    var iter = querySolution.varNames();
                    while(iter.hasNext()) {
                        var varName = iter.next();
                        var rdfNode = querySolution.get(varName);
                        obj[varName] = rdfNode;
                    }

                    arr.push(obj);
                }

                var r = arr.map(function(obj) {
                    var o = {};
                    for(var i in obj) {
                        o[i] = ''+obj[i].toString();
                    }
                    return o;
                });
                print(response).text(JSON.stringify(r, null, 2));

                qe.close();
                return arr;

            }
            

        }
    }
};

// simple syntax sugar
function print(response) {
    return {
        html: function(str) {
            if(str) {
                response.setContentType('text/html');
                response.getWriter().println(''+str);
            }
        },
        text: function(str) {
            if(str) {
                response.setContentType('text/plain');
                response.getWriter().println(''+str);
            }
        },
        json: function(j) {
            if(j) {
                var jsonString = JSON.stringify(j);
                response.setContentType("application/json");
                response.getWriter().println(jsonString);
            }
        }
    };
}
