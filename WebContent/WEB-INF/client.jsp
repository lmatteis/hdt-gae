<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Linked Data Fragments client</title>
  <script src="/jquery-2.1.0.js"></script>
  <script src="/ldf-client.js"></script>
  <script src="/ldf-client-ui.js"></script>
  <script>
    // Set the configuration of the Linked Data Fragments Client.
    var ldfConfig = { startFragment: window.location.href
                      };
    // Activate the UI for the .ldf-client element on load.
    jQuery(function ($) {
      new LinkedDataFragmentsClientUI($('.ldf-client'), ldfConfig).activate();
    });
  </script>
  <link rel="stylesheet" href="/ldf-client.css" />
</head>
<body>
  <h1>Linked Data Fragments client</h1>
  <fieldset class="ldf-client">
    <textarea class="query">
select * where {
  ?s ?p ?o
}
limit 20
    </textarea>
    <button class="execute">Execute query</button>
    <pre class="results"></pre>
    <pre class="log"></pre>
  </fieldset>
</body>
</html>

