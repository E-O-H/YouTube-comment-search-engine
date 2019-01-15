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
  <h1>Youtube Comment Searcher</h1>
  <form method=GET action="${SCRIPT_NAME}">
    <h2>
      <b>Please type your query here: </b>
      <input type="text" name="commentQuery" size=50>
              <input type="submit" value="Search"><br><hr>
      <b>Search filters: <br></b>
    </h2>
    <table>
      <tr><td>Username:<input type="text" name="usernameQuery" size=20></td>
          <td>User ID:<input type="text" name="userIdQuery" size=20></td>
          <td>Video title:<input type="text" name="videoTitleQuery" size=20></td>
          <td>Video ID:<input type="text" name="videoIdQuery" size=20></td>
          <td>Channel title:<input type="text" name="channelTitleQuery" size=20></td>
          <td>Channel ID:<input type="text" name="channelIdQuery" size=20></td></tr>
    </table>
  </form>
(Tips: you can leave the search box blank and only fill in the filters, e.g. to search for all comments from a user. To filter for multiple users/videos/channels, separate them with spaces; to match exact names, use double quotes.)
<hr>
WRITE_HTML

# If there is a non-empty query string submitted in URL,
# do the search and output the result.
if [ -n "$QUERY_STRING" ]; then
  # Extract the input value of the query
  commentQuery=`echo $QUERY_STRING | sed -n 's/^.*commentQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  usernameQuery=`echo $QUERY_STRING | sed -n 's/^.*usernameQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  userIdQuery=`echo $QUERY_STRING | sed -n 's/^.*userIdQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  videoTitleQuery=`echo $QUERY_STRING | sed -n 's/^.*videoTitleQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  videoIdQuery=`echo $QUERY_STRING | sed -n 's/^.*videoIdQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  channelTitleQuery=`echo $QUERY_STRING | sed -n 's/^.*channelTitleQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`
  channelIdQuery=`echo $QUERY_STRING | sed -n 's/^.*channelIdQuery=\([^&]*\).*$/\1/p' \
                | sed 's/+/ /g' | sed 's/%2B/+/g' | sed 's/%22/\"/g' | sed 's/%20/ /g'`

  classpath="/home/ct1856/public_html/java-bin/"
  indexpath="/home/ct1856/public_html/WSE-project-index-files/index"
  java -cp "${classpath}/args4j-2.33.jar:${classpath}/jsoup-1.11.3/jsoup-1.11.3.jar:${classpath}/lucene-6.6.0/core/lucene-core-6.6.0.jar:${classpath}/lucene-6.6.0/queryparser/lucene-queryparser-6.6.0.jar:${classpath}/lucene-6.6.0/highlighter/lucene-highlighter-6.6.0.jar:${classpath}/lucene-6.6.0/memory/lucene-memory-6.6.0.jar:${classpath}/gson-2.6.2.jar:${classpath}:bin:." youtubesearcher.YoutubeRetriever -i "$indexpath" -q "$commentQuery" -un "$usernameQuery" -ui "$userIdQuery" -vt "$videoTitleQuery" -vi "$videoIdQuery" -ct "$channelTitleQuery" -ci "$channelIdQuery"
fi

echo '</body>'
echo '</html>'

exit 0
