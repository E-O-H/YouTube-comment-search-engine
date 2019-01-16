To build index:

$ LIBRARY_PATH="lib"
$ java -cp "${LIBRARY_PATH}/args4j-2.33.jar;${LIBRARY_PATH}/jsoup-1.11.3/jsoup-1.11.3.jar;${LIBRARY_PATH}/lucene-6.6.0/core/lucene-core-6.6.0.jar;${LIBRARY_PATH}/gson-2.6.2.jar;${LIBRARY_PATH};bin;." youtubeIndexer.YoutubeIndexer [-c | -v] -i ID [-p INDEX_PATH] [-k API_KEY] [-h]

Required Options:
Exactly one scope option must be provided (-c or -v).
-v (-video): Specify the scope is a video (ID is videoID).
-c (-channel): Specify the scope is a channel (ID is channelID).
-i (-id): Provide an ID.

Optional Options:
-p (-path): Path to the directory to save index files. Default is "index".
-k (-api-key): You can provide an API key for use instead of the built-in one.
-h (-help): Print help message.


To search index:

$ LIBRARY_PATH="lib"
$ java -cp "${LIBRARY_PATH}/args4j-2.33.jar;${LIBRARY_PATH}/jsoup-1.11.3/jsoup-1.11.3.jar;${LIBRARY_PATH}/lucene-6.6.0/core/lucene-core-6.6.0.jar;${LIBRARY_PATH}/lucene-6.6.0/queryparser/lucene-queryparser-6.6.0.jar;${LIBRARY_PATH}/lucene-6.6.0/highlighter/lucene-highlighter-6.6.0.jar;${LIBRARY_PATH}/lucene-6.6.0/memory/lucene-memory-6.6.0.jar;${LIBRARY_PATH}/lucene-6.6.0/suggest/lucene-suggest-6.6.0.jar;${LIBRARY_PATH};bin;." youtubesearcher.YoutubeRetriever -i INDEX_PATH -q QUERY_STRING [-un USERNAME] [-ui USER_ID] [-vt VIDEO_TITLE] [-vi VIDEO_ID] [-ct CHANNEL_TITLE] [-ci CHANNEL_ID] [-p PAGE] [-m HITS_PER_PAGE] [-w CGI_URL] [-d DICTIONARY] [-h]

Required Options:
-i (-index): Path to the index directory.
-q (-query): The search query string.

Optional Options:
-un (-username): Usernames to filter the search result. Double quote a username for exact match.
-ui (-userId): A list of user IDs to filter the search result. Separated by space.
-vt (-video-title): Video titles to filter the search result. Double quote a title for exact match.
-vi (-videoId): A list of video IDs to filter the search result. Separated by space.
-ct (-channel-title): Channel titles to filter the search result. Double quote a name for exact match.
-ci (-channelId): A list of channel IDs to filter the search result. Separated by space.
-p (-page): Page number of the search results to output.
-w (-webpage-URL): URL of the search engine webpage; needed to correctly render links.
-d (-dictionary): Path to the dictionary index used for spellchecking and suggestion. If not provided this feature will be disabled.
-m (-max): Maximum number of search results to output.
-h (-help): Print help message.