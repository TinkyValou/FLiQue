package org.ods.start;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.EndpointManager;
import com.fluidops.fedx.FedXFactory;
import com.fluidops.fedx.algebra.StatementSource;
import com.fluidops.fedx.optimizer.SourceSelection;
import com.fluidops.fedx.structures.QueryInfo;
import org.aksw.simba.start.QueryProvider;
import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.ods.core.license.LicenseChecker;
import org.ods.core.relaxation.QueryRelaxationLattice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;

public class QueryEvaluation {
	protected static final Logger log = LoggerFactory.getLogger(QueryEvaluation.class);
	static {
		try {
			ClassLoader.getSystemClassLoader().loadClass("org.slf4j.LoggerFactory"). getMethod("getLogger", ClassLoader.getSystemClassLoader().loadClass("java.lang.String")).
			 invoke(null,"ROOT");
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	QueryProvider qp;

	public QueryEvaluation() throws Exception {
		qp = new QueryProvider("../queries/");
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception 
	{
		String cfgName = args[0];
		String repfile = args.length > 1 ? args[1] : null;
		
		String host = "localhost";
		String queries = "CH6";
	
		List<String> endpointsMin2 = Arrays.asList(
			 "http://" + host + ":8890/sparql",
			 "http://" + host + ":8891/sparql",
			 "http://" + host + ":8892/sparql",
			 "http://" + host + ":8893/sparql",
			 "http://" + host + ":8894/sparql",
			 "http://" + host + ":8895/sparql",
			 "http://" + host + ":8896/sparql",
			 "http://" + host + ":8897/sparql",
			 "http://" + host + ":8898/sparql",
			 "http://" + host + ":8899/sparql",
			 "http://" + host + ":8889/sparql"
		);
		
		List<String> endpoints = endpointsMin2;
		
		Map<String, List<List<Object>>> reports = multyEvaluate(queries, 1, cfgName, endpoints);

		for (Map.Entry<String, List<List<Object>>> e : reports.entrySet())
		{
			List<List<Object>> report = e.getValue();
			String r = printReport(report);
			log.info(r);
			if (null != repfile) {
				FileUtils.write(new File(repfile + "-" + e.getKey() + ".csv"), r);
			}
		}

		System.exit(0);
	}
	
	public Map<String, List<List<Object>>> evaluate(String queries, String cfgName, List<String> endpoints) throws Exception {
		List<List<Object>> report = new ArrayList<List<Object>>();
		List<List<Object>> sstreport = new ArrayList<List<Object>>();
		Map<String, List<List<Object>>> result = new HashMap<String, List<List<Object>>>();
		result.put("report", report);
		result.put("sstreport", sstreport);
		
		List<String> qnames = Arrays.asList(queries.split(" "));
		for (String curQueryName : qnames)
		{
			List<Object> reportRow = new ArrayList<Object>();
			report.add(reportRow);
			String curQuery = qp.getQuery(curQueryName);
			reportRow.add(curQueryName);
			
			List<Object> sstReportRow = new ArrayList<Object>();
			sstreport.add(sstReportRow);
			sstReportRow.add(curQueryName);
			
			Config config = new Config(cfgName);
			SailRepository repo = null;
			TupleQueryResult res = null;
			
			try {
				repo = FedXFactory.initializeSparqlFederation(config, endpoints);
				TupleQuery query = repo.getConnection().prepareTupleQuery(QueryLanguage.SPARQL, curQuery);
			   	long startTime = System.currentTimeMillis();
			   	res = query.evaluate();
			   	//TODO Remove that !
				// QueryRelaxationLattice relaxationLattice = new QueryRelaxationLattice(curQuery );
			   	// This is where FLiQuE is inserted
				QueryInfo queryInfo = QueryInfo.queryInfo.get();
				SourceSelection sourceSelection = queryInfo.getSourceSelection();
				Map<StatementPattern, List<StatementSource>> stmtToSources = sourceSelection.getStmtToSources();
				log.info(stmtToSources.toString());
				// 1. Verifier licences des sources
				LicenseChecker licenseChecker = new LicenseChecker("summaries/fedbench.n3");
				EndpointManager endpointManager = queryInfo.getFedXConnection().getEndpointManager();
				Set<String> consistentLicenses = licenseChecker.getConsistentLicenses(sourceSelection, endpointManager);
				/*
				while (consistentLicenses.isEmpty()) {
					// a license compatible with licenses of sources does not exists
					// We need to eliminate sources
					HashMap<String, Integer> endpointLicenseConflicts = licenseChecker.getEndpointlicenseConflicts();
					ArrayList<String> sourcesToRemove = licenseChecker.getSourcesToRemove(endpointLicenseConflicts);
					//remove endpoints
					endpoints.removeIf(enpoint -> (sourcesToRemove.contains(enpoint)));
					// on retry
					repo = FedXFactory.initializeSparqlFederation(config, endpoints);
					query = repo.getConnection().prepareTupleQuery(QueryLanguage.SPARQL, curQuery);
					res = query.evaluate();
					queryInfo = QueryInfo.queryInfo.get();
					sourceSelection = queryInfo.getSourceSelection();
					stmtToSources = sourceSelection.getStmtToSources();
					log.info(stmtToSources.toString());
					// ReVerifier licences des sources
					endpointManager = queryInfo.getFedXConnection().getEndpointManager();
					consistentLicenses = licenseChecker.getConsistentLicenses(sourceSelection, endpointManager);
				}
				*/
				// Here, we resolved all license conflicts
				while (!res.hasNext()) {
					// Here we relax the query until we get at least one result
					break;
				}

				// Now we can execute the query with FedX
			   	long count = 0;
				// TODO Uncomment next to execute query
			    while (res.hasNext()) {
			    	BindingSet row = res.next();
			    	System.out.println(count+": "+ row);
			    	count++;
			    }
			    long runTime = System.currentTimeMillis() - startTime;
			    reportRow.add((Long)count); reportRow.add((Long)runTime);
			    sstReportRow.add((Long)count);
			    sstReportRow.add(queryInfo.numSources.longValue());
			    sstReportRow.add(queryInfo.totalSources.longValue());
			    log.info(curQueryName + ": Query exection time (msec): "+ runTime + ", Total Number of Records: " + count + ", Source count: " + queryInfo.numSources.longValue());
				log.info(curQueryName + ": Query result have to be protected with one of the following licenses:" + licenseChecker.getLabelLicenses(consistentLicenses));
			} catch (Throwable e) {
				e.printStackTrace();
				log.error("", e);
				File f = new File("results/" + curQueryName + ".error.txt");
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(os);
				e.printStackTrace(ps);
				ps.flush();
				FileUtils.write(f, os.toString("UTF8"));
				reportRow.add(null); reportRow.add(null);
			} finally {
				if (null != res) {
		    		res.close();
		    	}
				
		    	if (null != repo) {
		    	    repo.shutDown();
		    	}
	        }
		}
		return result;
	}
	
	static Map<String, List<List<Object>>> multyEvaluate(String queries, int num, String cfgName, List<String> endpoints) throws Exception {
		QueryEvaluation qeval = new QueryEvaluation();

		Map<String, List<List<Object>>> result = null;
		for (int i = 0; i < num; ++i) {
			Map<String, List<List<Object>>> subReports = qeval.evaluate(queries, cfgName, endpoints);
			if (i == 0) {
				result = subReports;
			} else {
				//assert(report.size() == subReport.size());
				for (Map.Entry<String, List<List<Object>>> e : subReports.entrySet())
				{
					List<List<Object>> subReport = e.getValue();
					for (int j = 0; j < subReport.size(); ++j) {
						List<Object> subRow = subReport.get(j);
						List<Object> row = result.get(e.getKey()).get(j);
						row.add(subRow.get(2));
					}
				}
			}
		}
		
		return result;
	}
	
	static String printReport(List<List<Object>> report) {
		if (report.isEmpty()) return "";
		
		StringBuilder sb = new StringBuilder();
		sb.append("Query,#Results");
		
		List<Object> firstRow = report.get(0);
		for (int i = 2; i < firstRow.size(); ++i) {
			sb.append(",Sample #").append(i - 2);
		}
		sb.append("\n");
		for (List<Object> row : report) {
			for (int c = 0; c < row.size(); ++c) {
				sb.append(row.get(c));
				if (c != row.size() - 1) {
					sb.append(",");
				}
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}