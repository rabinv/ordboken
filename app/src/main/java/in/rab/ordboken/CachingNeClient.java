package in.rab.ordboken;

import android.util.LruCache;
import android.util.Pair;

import java.io.IOException;

public class CachingNeClient extends NeClient {
    private final LruCache<String, NeWord> mCache;
    private final LruCache<Pair<String, Integer>, NeSearchResult[]>  mSearchResultCache;

    public CachingNeClient(Auth auth) {
        super(auth);

        mCache = new LruCache<String, NeWord>(25);
        mSearchResultCache = new LruCache<Pair<String, Integer>, NeSearchResult[]>(25);
    }

    @Override
    public NeWord fetchWord(String wordUrl)
            throws LoginException, ParserException, IOException {
        NeWord word;

        word = mCache.get(wordUrl);
        if (word == null) {
            word = super.fetchWord(wordUrl);
            mCache.put(wordUrl, word);
        }

        return word;
    }

    @Override
    public NeSearchResult[] fetchSearchResults(String query, int count)
            throws ParserException, IOException {
        Pair<String, Integer> key = Pair.create(query, count);
        NeSearchResult[] results;

        results = mSearchResultCache.get(key);
        if (results == null) {
            results = super.fetchSearchResults(query, count);
            mSearchResultCache.put(key, results);
        }

        return results;
    }
}
