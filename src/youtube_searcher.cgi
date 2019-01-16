#!/bin/bash 

echo "Content-type: text/html"
echo ""

cat << HTML_PART_1
<!DOCTYPE html>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Youtube Comment Searcher</title>
  </head>
<body>
  <center><h1>Youtube Comment Searcher</h1></center>
  <table border=4>
    <td>
      <form method=GET action="${SCRIPT_NAME}">
        <h2>
          <b>Please type your query here: </b>
          <input type="text" name="commentQuery" size=50>
                  <input type="submit" value="Search"><br><hr>
          <b>Search filters: <br></b>
        </h2>
        <table>
          <tr><td>Username:<input type="text" name="usernameQuery" size=20></td>
              <td>User ID:<input type="text" name="userIdQuery" size=19></td>
              <td>Video title:<input type="text" name="videoTitleQuery" size=20></td>
              <td>Video ID:<input type="text" name="videoIdQuery" size=19></td>
              <td>Channel title:<input type="text" name="channelTitleQuery" size=20></td>
              <td>Channel ID:<input type="text" name="channelIdQuery" size=19></td></tr>
        </table>
      </form>
    </td>
    <td>
      <form method=GET action="${SCRIPT_NAME}">
        <h2>
          Add Index here:<br>
        </h2>
          <b>Index scope </b>
          <input type="radio" name="indexScope" value="video" checked="checked">Video
          <input type="radio" name="indexScope" value="channel">Channel
          <br>
          <b>Video/Channel ID </b>
          <input type=text name="indexScopeId" size=12>
          <br>
          <input type=submit value="Index all comments in specified scope">
      </form>
HTML_PART_1

if [[ -n "$QUERY_STRING" ]]; then
  # Check if the query string contains index parameters
  indexScopeId=`echo $QUERY_STRING | sed -n 's/^.*indexScopeId=\([^&]*\).*$/\1/p'`
  if [[ -n "$indexScopeId" ]]; then
    indexScope=`echo $QUERY_STRING | sed -n 's/^.*indexScope=\([^&]*\).*$/\1/p'`
    case $indexScope in
      video)
               indexScopeFlag='-v'
               ;;
      channel)
               indexScopeFlag='-c'
               ;;
      *)
               ;;
    esac
    
    classpath="/home/ct1856/public_html/java-bin/"
    indexpath="/home/ct1856/public_html/WSE-project-index-files/index"
    # This trick allows a program to run in the background while at the same time, output can be captured
    # in real-time before the program is finished, which allows the shell script to move on (in this case it
    # captures the URL that hosts the output of the indexer and renders a hyper-link with it on the webpage).
    # Without using this trick, the webpage will stuck in loading until the indexer finishes running.
    # (Note the exec command here is not used to execute a program; it is used to open a file descriptor ( 
    # which is fd3 in this case) and associate it with a file (which is a virtual file opened by the 
    # process substitution in this case) This usage of exec is provided in bash and ksh; process substitution
    # is only provided in bash.)
    exec 3< <(java -cp "${classpath}/args4j-2.33.jar:${classpath}/jsoup-1.11.3/jsoup-1.11.3.jar:${classpath}/lucene-6.6.0/core/lucene-core-6.6.0.jar:${classpath}/gson-2.6.2.jar:${classpath}:bin:." youtubesearcher.YoutubeIndexer -p "$indexpath" "$indexScopeFlag" -i "$indexScopeId" \
              2>&1 | nc seashells.io 1337)
    sleep 0.1                                  # sleep 100ms to make sure the output by nc (our URL) is already written to fd3.
    read <&3 discard discard indexProgressUrl  # Read one line from fd3 and assign content starting from the 3rd token to $indexProgressUrl.
    echo "<center><a href=$indexProgressUrl>See the index progress in real-time</a></center>"
  fi
fi

cat << HTML_PART_2
    </td>
  </table>
<b>Can't find the comment you are looking for? Maybe it hasn't been indexed.</b> If the video containing the comments you are searching for has not yet been indexed, you should first add the video to the index in the right pannel. It is also supported to index in the scope of a channel, which will add all videos in the channel at once to the index, but please think twice before you decide to use this feature, as this will take a REALLY long time if the channel has a lot of videos. You can find the video ID in the URL to a video; for channel IDs, you need to find them in the page source in a variable named "externalId".<br>
<i>Tips: you can leave the search box blank and only fill in the filters, e.g. to search for all comments from a user. To filter for multiple users/videos/channels, separate them with spaces; to match exact names, use double quotes.</i>
<hr>
<style type="text/css">
  a {text-decoration: none;}
  a:hover {text-decoration: underline;}
</style>
HTML_PART_2

# If there is a non-empty query string submitted in URL,
# do the search and output the result.
if [[ -n "$QUERY_STRING" ]]; then
  # Extract the input values of the query
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
  page=`echo $QUERY_STRING | sed -n 's/^.*page=\([^&]*\).*$/\1/p'`
  max=`echo $QUERY_STRING | sed -n 's/^.*max=\([^&]*\).*$/\1/p'`

  if [[ -z "$page" ]]; then
    page=1
  fi
  if [[ -z "$max" ]]; then
    max=20
  fi

  if [[ ( -n "$commentQuery" ) || ( -n "$usernameQuery" ) || ( -n "$userIdQuery" ) || ( -n "$videoTitleQuery" ) \
        || ( -n "$videoIdQuery" ) || ( -n "$channelTitleQuery" ) || ( -n "$channelIdQuery" ) ]]; then
    classpath="/home/ct1856/public_html/java-bin/"
    indexpath="/home/ct1856/public_html/WSE-project-index-files/index"
    java -cp "${classpath}/args4j-2.33.jar:${classpath}/jsoup-1.11.3/jsoup-1.11.3.jar:${classpath}/lucene-6.6.0/core/lucene-core-6.6.0.jar:${classpath}/lucene-6.6.0/queryparser/lucene-queryparser-6.6.0.jar:${classpath}/lucene-6.6.0/highlighter/lucene-highlighter-6.6.0.jar:${classpath}/lucene-6.6.0/memory/lucene-memory-6.6.0.jar:${classpath}/gson-2.6.2.jar:${classpath}:bin:." youtubesearcher.YoutubeRetriever -i "$indexpath" -w "$SCRIPT_NAME" -q "$commentQuery" -un "$usernameQuery" -ui "$userIdQuery" -vt "$videoTitleQuery" -vi "$videoIdQuery" -ct "$channelTitleQuery" -ci "$channelIdQuery" -p "$page" -m "$max"
  fi
fi

echo '</body>'
echo '</html>'

exit 0
