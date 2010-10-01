package org.persvr.store;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class LuceneSearch {
	String indexLocation;
	static IndexWriter writer;
	static Directory directory;
	String tableName;
	public LuceneSearch(String location) throws Exception {
		indexLocation = location;
		synchronized(getClass()){
			if(writer == null){
				File lock = new File(location + "/write.lock");
				if(lock.isFile()){
					lock.delete();
				}
				writer = new IndexWriter(indexLocation, new StandardAnalyzer(), IndexWriter.MaxFieldLength.LIMITED);
				directory = writer.getDirectory();
			}
		}
	}

	public void create(String id, Scriptable object) throws Exception {
		Document doc = new Document();
		doc.add(new Field("id", id, Field.Store.YES, Field.Index.NOT_ANALYZED));
		for(Object key : object.getIds()){
			Object value = object.get(key.toString(), object);
			String strValue = "";
			if (value instanceof Scriptable && "Date".equals(((Scriptable)value).getClassName())) {
				double time = (Double) ((Function) ScriptableObject.getProperty((Scriptable)value,"getTime")).call(org.mozilla.javascript.Context.enter(), null, (Scriptable)value, new Object[]{});
				strValue = DateTools.timeToString((long) time, DateTools.Resolution.MILLISECOND);
			} else {
				strValue = "" + value;
			}
			doc.add(new Field(key.toString(), strValue, Field.Store.NO, Field.Index.ANALYZED));
		}
		
		writer.addDocument(doc);
	}

	public void commitTransaction() throws SQLException {
		try {
			writer.commit();
		} catch (CorruptIndexException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void remove(String objectId) throws Exception {
		writer.deleteDocuments(new Term("id", objectId));
	}

	public Scriptable query(String queryString, String[] fields, long start, long end, String sort) throws Exception {
		final IndexSearcher search = new IndexSearcher(directory);
		Analyzer analyzer = new StandardAnalyzer();
		Query q = new MultiFieldQueryParser(fields, analyzer).parse(queryString);
		final TopDocs topDocs = search.search(q, end > 1000 ? 100000 : (int) end);
		final ScriptableObject results = new NativeObject();
		ScriptableObject some = new BaseFunction(){
			public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args){
				Function callback = (Function) args[0];
				try {
					for(ScoreDoc scoreDoc : topDocs.scoreDocs){
						Document doc = search.doc(scoreDoc.doc);
						try{
							Long.parseLong(doc.get("id"));
							callback.call(cx, scope, results, new Object[]{doc.get("id")});
						}
						catch(NumberFormatException e){
							
						}
					}
				} catch (CorruptIndexException e) {
					ScriptRuntime.constructError("Error", e.getMessage());
				} catch (IOException e) {
					ScriptRuntime.constructError("Error", e.getMessage());				
				}
				return null;
			}
		};
		results.put("some", results, some);
		results.put("totalCount", results, topDocs.totalHits);
		results.setAttributes("totalCount", ScriptableObject.DONTENUM);
		return results;
	}

	public Scriptable query(String queryString, String field, long start, long end, String sort) throws Exception {
		return this.query(queryString, new String[]{ field }, start, end, sort);
	}
}
