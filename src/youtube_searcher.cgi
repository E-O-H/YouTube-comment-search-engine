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
      <tr><td><b>Please type your query here:</b></td></tr>
      <tr><td><input type="text" name="commentQuery" size=20>
              <input type="submit" value="Search"></td></tr>
      <tr><td><b>Search filters:</b> 
              (Tips: to filter multiple users/videos/channels, 
              separate them with space; 
              to match exact names use double quotes)</td></tr>
      <tr><td>Username:<input type="text" name="usernameQuery" size=10></td>
          <td>User ID:<input type="text" name="userIdQuery" size=10></td>
          <td>Video title:<input type="text" name="videoTitleQuery" size=10></td>
          <td>Video ID:<input type="text" name="videoIdQuery" size=10></td>
          <td>Channel title:<input type="text" name="channelTitleQuery" size=10></td>
          <td>Channel ID:<input type="text" name="channelIdQuery" size=10></td></tr>
    </table>
  </form>
WRITE_HTML

# If there is a non-empty query string submitted in URL,
# do the search and output the result.
if [ -n "$QUERY_STRING" ]; then
   # Extract the input value of the query
   commentQuery=`echo $QUERY_STRING | sed -n 's/^.*commentQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   usernameQuery=`echo $QUERY_STRING | sed -n 's/^.*usernameQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   userIdQuery=`echo $QUERY_STRING | sed -n 's/^.*userIdQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   videoTitleQuery=`echo $QUERY_STRING | sed -n 's/^.*videoTitleQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   videoIdQuery=`echo $QUERY_STRING | sed -n 's/^.*commentQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   commentQuery=`echo $QUERY_STRING | sed -n 's/^.*commentQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`
   commentQuery=`echo $QUERY_STRING | sed -n 's/^.*commentQuery=\([^&]*\).*$/\1/p' | sed 's/%20/ /g'`

   classpath="/home/ct1856/public_html/java-bin/"
   indexpath="/home/ct1856/public_html/WSE-hw1-index-files/"
   java -cp "${classpath}:${classpath}args4j-2.33.jar:${classpath}jsoup-1.11.3/jsoup-1.11.3.jar:\
${classpath}lucene-6.6.0/core/lucene-core-6.6.0.jar:${classpath}lucene-6.6.0/queryparser/lucene-queryparser-6.6.0.jar"\
 Retriever -i $indexpath -q $keyword
fi

echo '</body>'
echo '</html>'

exit 0
