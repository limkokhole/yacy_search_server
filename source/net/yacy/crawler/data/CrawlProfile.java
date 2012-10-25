// CrawlProfile.java
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.crawler.data;

import java.text.DateFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.server.serverObjects;

public class CrawlProfile extends ConcurrentHashMap<String, String> implements Map<String, String> {

    private static final long serialVersionUID = 5527325718810703504L;

    public static final String  MATCH_ALL_STRING    = ".*";
    public static final String  MATCH_NEVER_STRING  = "";
    public static final Pattern MATCH_ALL_PATTERN   = Pattern.compile(MATCH_ALL_STRING);
    public static final Pattern MATCH_NEVER_PATTERN = Pattern.compile(MATCH_NEVER_STRING);

    // this is a simple record structure that hold all properties of a single crawl start
    public static final String HANDLE           = "handle";
    public static final String NAME             = "name";
    public static final String DEPTH            = "generalDepth";
    public static final String DIRECT_DOC_BY_URL= "directDocByURL";
    public static final String RECRAWL_IF_OLDER = "recrawlIfOlder";
    public static final String DOM_MAX_PAGES    = "domMaxPages";
    public static final String CRAWLING_Q       = "crawlingQ";
    public static final String PUSH_SOLR        = "pushSolr";
    public static final String INDEX_TEXT       = "indexText";
    public static final String INDEX_MEDIA      = "indexMedia";
    public static final String STORE_HTCACHE    = "storeHTCache";
    public static final String REMOTE_INDEXING  = "remoteIndexing";
    public static final String XSSTOPW          = "xsstopw";
    public static final String XDSTOPW          = "xdstopw";
    public static final String XPSTOPW          = "xpstopw";
    public static final String CACHE_STRAGEGY   = "cacheStrategy";
    public static final String CRAWLER_URL_MUSTMATCH         = "crawlerURLMustMatch";
    public static final String CRAWLER_URL_MUSTNOTMATCH      = "crawlerURLMustNotMatch";
    public static final String CRAWLER_IP_MUSTMATCH          = "crawlerIPMustMatch";
    public static final String CRAWLER_IP_MUSTNOTMATCH       = "crawlerIPMustNotMatch";
    public static final String CRAWLER_COUNTRY_MUSTMATCH     = "crawlerCountryMustMatch";
    public static final String CRAWLER_URL_NODEPTHLIMITMATCH = "crawlerNoLimitURLMustMatch";
    public static final String INDEXING_URL_MUSTMATCH        = "indexURLMustMatch";
    public static final String INDEXING_URL_MUSTNOTMATCH     = "indexURLMustNotMatch";
    public static final String COLLECTIONS = "collections";

    private Pattern crawlerurlmustmatch = null, crawlerurlmustnotmatch = null;
    private Pattern crawleripmustmatch = null, crawleripmustnotmatch = null;
    private Pattern crawlernodepthlimitmatch = null;
    private Pattern indexurlmustmatch = null, indexurlmustnotmatch = null;

    public final static class DomProfile {

        public String referrer;
        public int depth, count;

        public DomProfile(final String ref, final int d) {
            this.referrer = ref;
            this.depth = d;
            this.count = 1;
        }

        public void inc() {
            this.count++;
        }

    }

    private final Map<String, DomProfile> doms;

    /**
     * Constructor which creates CrawlPofile from parameters.
     * @param name name of the crawl profile
     * @param startURL root URL of the crawl
     * @param crawlerUrlMustMatch URLs which do not match this regex will be ignored in the crawler
     * @param crawlerUrlMustNotMatch URLs which match this regex will be ignored in the crawler
     * @param crawlerIpMustMatch IPs from URLs which do not match this regex will be ignored in the crawler
     * @param crawlerIpMustNotMatch IPs from URLs which match this regex will be ignored in the crawler
     * @param crawlerCountryMustMatch URLs from a specific country must match
     * @param crawlerNoDepthLimitMatch if matches, no depth limit is applied to the crawler
     * @param indexUrlMustMatch URLs which do not match this regex will be ignored for indexing
     * @param indexUrlMustNotMatch URLs which match this regex will be ignored for indexing
     * @param depth height of the tree which will be created by the crawler
     * @param directDocByURL if true, then linked documents that cannot be parsed are indexed as document
     * @param recrawlIfOlder documents which have been indexed in the past will
     * be indexed again if they are older than the time (ms) in this parameter
     * @param domMaxPages maximum number from one domain which will be indexed
     * @param crawlingQ true if URLs containing questionmarks shall be indexed
     * @param indexText true if text content of URL shall be indexed
     * @param indexMedia true if media content of URL shall be indexed
     * @param storeHTCache true if content chall be kept in cache after indexing
     * @param remoteIndexing true if part of the crawl job shall be distributed
     * @param xsstopw true if static stop words shall be ignored
     * @param xdstopw true if dynamic stop words shall be ignored
     * @param xpstopw true if parent stop words shall be ignored
     * @param cacheStrategy determines if and how cache is used loading content
     * @param collections a comma-separated list of tags which are attached to index entries
     */
    public CrawlProfile(
                 String name,
                 final String crawlerUrlMustMatch, final String crawlerUrlMustNotMatch,
                 final String crawlerIpMustMatch, final String crawlerIpMustNotMatch,
                 final String crawlerCountryMustMatch, final String crawlerNoDepthLimitMatch,
                 final String indexUrlMustMatch, final String indexUrlMustNotMatch,
                 final int depth,
                 final boolean directDocByURL,
                 final long recrawlIfOlder /*date*/,
                 final int domMaxPages,
                 final boolean crawlingQ,
                 final boolean indexText,
                 final boolean indexMedia,
                 final boolean storeHTCache,
                 final boolean remoteIndexing,
                 final boolean xsstopw,
                 final boolean xdstopw,
                 final boolean xpstopw,
                 final CacheStrategy cacheStrategy,
                 final String collections) {
        super(40);
        if (name == null || name.isEmpty()) {
            throw new NullPointerException("name must not be null or empty");
        }
        if (name.length() > 256) name = name.substring(256);
        this.doms = new ConcurrentHashMap<String, DomProfile>();
        final String handle = Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(name)).substring(0, Word.commonHashLength);
        put(HANDLE,           handle);
        put(NAME,             name);
        put(CRAWLER_URL_MUSTMATCH,     (crawlerUrlMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : crawlerUrlMustMatch);
        put(CRAWLER_URL_MUSTNOTMATCH,  (crawlerUrlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerUrlMustNotMatch);
        put(CRAWLER_IP_MUSTMATCH,      (crawlerIpMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : crawlerIpMustMatch);
        put(CRAWLER_IP_MUSTNOTMATCH,   (crawlerIpMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerIpMustNotMatch);
        put(CRAWLER_COUNTRY_MUSTMATCH, (crawlerCountryMustMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerCountryMustMatch);
        put(CRAWLER_URL_NODEPTHLIMITMATCH, (crawlerNoDepthLimitMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerNoDepthLimitMatch);
        put(INDEXING_URL_MUSTMATCH, (indexUrlMustMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexUrlMustMatch);
        put(INDEXING_URL_MUSTNOTMATCH, (indexUrlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexUrlMustNotMatch);
        put(DEPTH,            depth);
        put(DIRECT_DOC_BY_URL, directDocByURL);
        put(RECRAWL_IF_OLDER, recrawlIfOlder);
        put(DOM_MAX_PAGES,    domMaxPages);
        put(CRAWLING_Q,       crawlingQ); // crawling of urls with '?'
        put(INDEX_TEXT,       indexText);
        put(INDEX_MEDIA,      indexMedia);
        put(STORE_HTCACHE,    storeHTCache);
        put(REMOTE_INDEXING,  remoteIndexing);
        put(XSSTOPW,          xsstopw); // exclude static stop-words
        put(XDSTOPW,          xdstopw); // exclude dynamic stop-word
        put(XPSTOPW,          xpstopw); // exclude parent stop-words
        put(CACHE_STRAGEGY,   cacheStrategy.toString());
        put(COLLECTIONS,      collections.trim().replaceAll(" ", ""));
    }

    /**
     * Constructor which creats a CrawlProfile from values in a Map.
     * @param ext contains values
     */
    public CrawlProfile(final Map<String, String> ext) {
        super(ext == null ? 1 : ext.size());
        if (ext != null) putAll(ext);
        this.doms = new ConcurrentHashMap<String, DomProfile>();
    }

    public void domInc(final String domain, final String referrer, final int depth) {
        final DomProfile dp = this.doms.get(domain);
        if (dp == null) {
            // new domain
            this.doms.put(domain, new DomProfile(referrer, depth));
        } else {
            // increase counter
            dp.inc();
        }
    }

    public String domName(final boolean attr, final int index){
        final Iterator<Map.Entry<String, DomProfile>> domnamesi = this.doms.entrySet().iterator();
        String domname="";
        Map.Entry<String, DomProfile> ey;
        DomProfile dp;
        int i = 0;
        while ((domnamesi.hasNext()) && (i < index)) {
            ey = domnamesi.next();
            i++;
        }
        if (domnamesi.hasNext()) {
            ey = domnamesi.next();
            dp = ey.getValue();
            domname = ey.getKey() + ((attr) ? ("/r=" + dp.referrer + ", d=" + dp.depth + ", c=" + dp.count) : " ");
        }
        return domname;
    }

    public void clearDoms() {
        this.doms.clear();
    }

    public DomProfile getDom(final String domain) {
        return this.doms.get(domain);
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final boolean value) {
        super.put(key, Boolean.toString(value));
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final int value) {
        super.put(key, Integer.toString(value));
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final long value) {
        super.put(key, Long.toString(value));
    }

    /**
     * Gets handle of the CrawlProfile.
     * @return handle of the profile
     */
    public String handle() {
        final String r = get(HANDLE);
        assert r != null;
        //if (r == null) return null;
        return r;
    }

    /**
     * get the collections for this crawl
     * @return a list of collection names
     */
    public String[] collections() {
        final String r = get(COLLECTIONS);
        if (r == null) return new String[0];
        return r.split(",");
    }

    /**
     * Gets the name of the CrawlProfile.
     * @return  name of the profile
     */
    public String name() {
        final String r = get(NAME);
        if (r == null) return "";
        return r;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern urlMustMatchPattern() {
        if (this.crawlerurlmustmatch == null) {
            final String r = get(CRAWLER_URL_MUSTMATCH);
            try {
                this.crawlerurlmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.crawlerurlmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlerurlmustmatch;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern urlMustNotMatchPattern() {
        if (this.crawlerurlmustnotmatch == null) {
            final String r = get(CRAWLER_URL_MUSTNOTMATCH);
            try {
                this.crawlerurlmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.crawlerurlmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlerurlmustnotmatch;
    }

    /**
     * Gets the regex which must be matched by IPs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern ipMustMatchPattern() {
        if (this.crawleripmustmatch == null) {
            final String r = get(CRAWLER_IP_MUSTMATCH);
            try {
                this.crawleripmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.crawleripmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawleripmustmatch;
    }

    /**
     * Gets the regex which must not be matched by IPs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern ipMustNotMatchPattern() {
        if (this.crawleripmustnotmatch == null) {
            final String r = get(CRAWLER_IP_MUSTNOTMATCH);
            try {
                this.crawleripmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.crawleripmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawleripmustnotmatch;
    }

    /**
     * get the list of countries that must match for the locations of the URLs IPs
     * @return a list of country codes
     */
    public String[] countryMustMatchList() {
        String countryMustMatch = get(CRAWLER_COUNTRY_MUSTMATCH);
        if (countryMustMatch == null) countryMustMatch = CrawlProfile.MATCH_NEVER_STRING;
        if (countryMustMatch.isEmpty()) return new String[0];
        String[] list = countryMustMatch.split(",");
        if (list.length == 1 && list.length == 0) list = new String[0];
        return list;
    }
    
    /**
     * If the regex matches with the url, then there is no depth limit on the crawl (it overrides depth == 0)
     * @return regex which must be matched
     */
    public Pattern crawlerNoDepthLimitMatchPattern() {
        if (this.crawlernodepthlimitmatch == null) {
            final String r = get(CRAWLER_URL_NODEPTHLIMITMATCH);
            try {
                this.crawlernodepthlimitmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.crawlernodepthlimitmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlernodepthlimitmatch;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be indexed.
     * @return regex which must be matched
     */
    public Pattern indexUrlMustMatchPattern() {
        if (this.indexurlmustmatch == null) {
            final String r = get(INDEXING_URL_MUSTMATCH);
            try {
                this.indexurlmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.indexurlmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexurlmustmatch;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be indexed.
     * @return regex which must not be matched
     */
    public Pattern indexUrlMustNotMatchPattern() {
        if (this.indexurlmustnotmatch == null) {
            final String r = get(INDEXING_URL_MUSTNOTMATCH);
            try {
                this.indexurlmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r);
            } catch (PatternSyntaxException e) { this.indexurlmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexurlmustnotmatch;
    }
    
    /**
     * Gets depth of crawl job (or height of the tree which will be
     * created by the crawler).
     * @return depth of crawl job
     */
    public int depth() {
        final String r = get(DEPTH);
        if (r == null) return 0;
        try {
            return Integer.parseInt(r);
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return 0;
        }
    }

    public boolean directDocByURL() {
        final String r = get(DIRECT_DOC_BY_URL);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public CacheStrategy cacheStrategy() {
        final String r = get(CACHE_STRAGEGY);
        if (r == null) return CacheStrategy.IFEXIST;
        try {
            return CacheStrategy.decode(Integer.parseInt(r));
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return CacheStrategy.IFEXIST;
        }
    }

    public void setCacheStrategy(final CacheStrategy newStrategy) {
        put(CACHE_STRAGEGY, newStrategy.toString());
    }

    /**
     * Gets the minimum age that an entry must have to be re-crawled.
     * @return time in ms
     */
    public long recrawlIfOlder() {
        // returns a long (millis) that is the minimum age that
        // an entry must have to be re-crawled
        final String r = get(RECRAWL_IF_OLDER);
        if (r == null) return 0L;
        try {
            final long l = Long.parseLong(r);
            return (l < 0) ? 0L : l;
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return 0L;
        }
    }

    public int domMaxPages() {
        // this is the maximum number of pages that are crawled for a single domain
        // if -1, this means no limit
        final String r = get(DOM_MAX_PAGES);
        if (r == null) return Integer.MAX_VALUE;
        try {
            final int i = Integer.parseInt(r);
            if (i < 0) return Integer.MAX_VALUE;
            return i;
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return Integer.MAX_VALUE;
        }
    }

    public boolean crawlingQ() {
        final String r = get(CRAWLING_Q);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean pushSolr() {
        final String r = get(PUSH_SOLR);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean indexText() {
        final String r = get(INDEX_TEXT);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean indexMedia() {
        final String r = get(INDEX_MEDIA);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean storeHTCache() {
        final String r = get(STORE_HTCACHE);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean remoteIndexing() {
        final String r = get(REMOTE_INDEXING);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean excludeStaticStopwords() {
        final String r = get(XSSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean excludeDynamicStopwords() {
        final String r = get(XDSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean excludeParentStopwords() {
        final String r = get(XPSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public static long getRecrawlDate(final long oldTimeMinutes) {
        return System.currentTimeMillis() - (60000L * oldTimeMinutes);
    }

    public static String mustMatchFilterFullDomain(final MultiProtocolURI uri) {
        String host = uri.getHost();
        if (host.startsWith("www.")) host = host.substring(4);
        String protocol = uri.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) protocol = "https?+";
        return new StringBuilder(host.length() + 20).append(protocol).append("://(www.)?").append(Pattern.quote(host)).append(".*").toString();
    }

    public static String mustMatchSubpath(final MultiProtocolURI uri) {
        String u = uri.toNormalform(true);
        if (!u.endsWith("/")) {int p = u.lastIndexOf("/"); if (p > 0) u = u.substring(0, p + 1);}
        return new StringBuilder(u.length() + 5).append(Pattern.quote(u)).append(".*").toString();
    }

    public static String siteFilter(final Set<? extends MultiProtocolURI> uris) {
        final StringBuilder filter = new StringBuilder();
        for (final MultiProtocolURI uri: uris) filter.append('|').append(mustMatchFilterFullDomain(uri));
        return filter.length() > 0 ? filter.substring(1) : CrawlProfile.MATCH_ALL_STRING;
    }

    public static String subpathFilter(final Set<? extends MultiProtocolURI> uris) {
        final StringBuilder filter = new StringBuilder();
        for (final MultiProtocolURI uri: uris) filter.append('|').append(mustMatchSubpath(uri));
        return filter.length() > 0 ? filter.substring(1) : CrawlProfile.MATCH_ALL_STRING;
    }

    public static final Set<String> ignoreNames = new HashSet<String>();
    static {
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_PROXY);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_REMOTE);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT);
        ignoreNames.add(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE);
        ignoreNames.add(CrawlSwitchboard.DBFILE_ACTIVE_CRAWL_PROFILES);
        ignoreNames.add(CrawlSwitchboard.DBFILE_PASSIVE_CRAWL_PROFILES);
    }

    public void putProfileEntry(
    		final String CRAWL_PROFILE_PREFIX,
            final serverObjects prop,
            final boolean active,
            final boolean dark,
            final int count,
            final int domlistlength) {

        prop.put(CRAWL_PROFILE_PREFIX + count + "_dark", dark ? "1" : "0");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_name", this.name());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_terminateButton", (!active || ignoreNames.contains(this.name())) ? "0" : "1");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_terminateButton_handle", this.handle());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_deleteButton", (active) ? "0" : "1");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_deleteButton_handle", this.handle());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_handle", this.handle());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_depth", this.depth());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_mustmatch", this.urlMustMatchPattern().toString());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_mustnotmatch", this.urlMustNotMatchPattern().toString());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_crawlingIfOlder", (this.recrawlIfOlder() == 0L) ? "no re-crawl" : DateFormat.getDateTimeInstance().format(this.recrawlIfOlder()));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_crawlingDomFilterDepth", "inactive");

        int i = 0;
        if (active && this.domMaxPages() > 0
                && this.domMaxPages() != Integer.MAX_VALUE) {
        String item;
        while (i <= domlistlength && !(item = this.domName(true, i)).isEmpty()){
            if (i == domlistlength) {
                item += " ...";
            }
            prop.putHTML(CRAWL_PROFILE_PREFIX + count + "_crawlingDomFilterContent_" + i + "_item", item);
            i++;
        }
        }

        prop.put(CRAWL_PROFILE_PREFIX+count+"_crawlingDomFilterContent", i);

        prop.put(CRAWL_PROFILE_PREFIX + count + "_crawlingDomMaxPages", (this.domMaxPages() == Integer.MAX_VALUE) ? "unlimited" : Integer.toString(this.domMaxPages()));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_withQuery", (this.crawlingQ()) ? "1" : "0");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_storeCache", (this.storeHTCache()) ? "1" : "0");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_indexText", (this.indexText()) ? "1" : "0");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_indexMedia", (this.indexMedia()) ? "1" : "0");
        prop.put(CRAWL_PROFILE_PREFIX + count + "_remoteIndexing", (this.remoteIndexing()) ? "1" : "0");
    }
}
