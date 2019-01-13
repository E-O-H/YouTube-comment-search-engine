#!/bin/bash 

echo "Content-type: text/html"
echo ""

cat << WRITE_HTML
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Youtube Comment Searcher</title>
    </head>
<body>
<h2><Youtube Comment Searcher/h2>
    <form method=GET action="${SCRIPT_NAME}">
        <table>
            <tr><td>Please type your query here:</td></tr>
	    <tr><td><input type="text" name="raw_input" size=20>
                    <input type="submit" value="Search"></td></tr>
        </table>
    </form>
WRITE_HTML

# If there is a not empty query string submitted in URL,
# do the search and output the result.
if [ -n "$QUERY_STRING" ]; then
   # Extract the input value of the query
   keyword=`echo $QUERY_STRING | sed -n 's/^.*raw_input=\([^&]*\).*$/\1/p' | sed "s/%20/ /g"`

   classpath="/home/ct1856/public_html/java-bin/"
   indexpath="/home/ct1856/public_html/WSE-hw1-index-files/"
   java -cp "${classpath}:${classpath}args4j-2.33.jar:${classpath}jsoup-1.11.3/jsoup-1.11.3.jar:\
${classpath}lucene-6.6.0/core/lucene-core-6.6.0.jar:${classpath}lucene-6.6.0/queryparser/lucene-queryparser-6.6.0.jar"\
 Retriever -i $indexpath -q $keyword
fi

echo '</body>'
echo '</html>'

exit 0
