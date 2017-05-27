package org.fhcrc.common.servlets;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import javax.servlet.ServletException;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.sling.jcr.api.SlingRepository;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SlingServlet(paths="/bin/fhcrc/userservlet", resourceTypes="/apps/common/components/content/userLister", extensions="html")
public class UserListerServlet extends SlingSafeMethodsServlet {

	private static final long serialVersionUID = -92472611L;
	private static final Logger log = LoggerFactory.getLogger(UserListerServlet.class);
	@Reference
	private SlingRepository repository;
	@Reference
	private ResourceResolverFactory resolverFactory;
	
	final String TEST_CASE = "/home/groups/geometrixx/communities";
	final String PN_USER_NAME = "profile/givenName";
	final String SOURCE_CENTERNET = "centernet";
	final String SOURCE_PUBLIC = "public";
	final String SOURCE_LABS = "research";
	final String PATH_CENTERNET = "/home/groups/c";
	final String PATH_PUBLIC = "/home/groups/p";
	final String PATH_LABS = "/home/groups/r";
	
	private ResourceResolver resResolver;
	private QueryBuilder queryBuilder;
	private HashMap<String,String> queryMap;
	private Session session;
	private TreeMap<String, List<Map<String,String>>> membersByGroup;
	private List<Hit> hitList;
	private String[] selectors;
	private Boolean isCenternet = false, isWww = false, isLabs = false;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
		
		PrintWriter writer = response.getWriter();
		resResolver = null;
		session = null;
		
		response.setHeader("Content-Type", "text-html");
		
		/* Get the selectors from the URL and use them to drive the search */
		selectors = request.getRequestPathInfo().getSelectors();
		List<String> selectorList = Arrays.asList(selectors);
		
		if (selectorList.contains("centernet")) {
			isCenternet = true;
		}
		if (selectorList.contains("www") || selectorList.contains("public")) {
			isWww = true;
		}
		if (selectorList.contains("labs") || selectorList.contains("research")) {
			isLabs = true;
		}
		
		/* If there are no selectors, inform the user to add one */
		if (!isCenternet && !isWww && !isLabs) {
			displayHelpMessage(writer);
			return;
		} else {
		
			try {
				
				/* Use the administrative resourceResolver to gain permission to the Users */
				resResolver = resolverFactory.getAdministrativeResourceResolver(null);
				session = resResolver.adaptTo(Session.class);
				queryBuilder = resResolver.adaptTo(QueryBuilder.class);
				queryMap = createQueryMap();
				
				hitList = queryBuilder.createQuery(PredicateGroup.create(queryMap), session).getResult().getHits();
				membersByGroup = createGroupList();
				
				/* Output the name of each group */
				for (Map.Entry<String, List<Map<String,String>>> entry : membersByGroup.entrySet()) {
					
					writer.print("<h2>");
					writer.print(entry.getKey());
					writer.print("</h2>");
					
					writer.print("<ul>");
					
					/* Output the names of the Users in each Group*/
					Iterator<Map<String,String>> people = entry.getValue().iterator();
					while (people.hasNext()) {
						
						Map<String, String> person = people.next();
						writer.print("<li>");
						writer.print(person.get("name"));
						writer.print("</li>");
						
					}
					
					writer.print("</ul>");
					
				}
				
			} catch (LoginException e) {
				
				log.error("User Lister loginException: ", e);
				
			} finally {
				
				/* Reset all important variables and logout our session */ 
				isCenternet = false;
				isWww = false;
				isLabs = false;
				
				if (session != null) {
					session.logout();
				}
				
				
			}
		}
		
	}
	
	/* A method to create a HashMap that is later turned into a Query */
	private HashMap<String,String> createQueryMap() {
		
		HashMap<String, String> map = new HashMap<String, String>();
		String sourcePath = TEST_CASE;
		
		if (isCenternet) {
			sourcePath = PATH_CENTERNET;
		} else if (isWww) {
			sourcePath = PATH_PUBLIC;
		} else if (isLabs) {
			sourcePath = PATH_LABS;
		}
		
		map.put("type","rep:Group");
		map.put("path", sourcePath);
	    
	    // Include all hits
	    map.put("p.limit", "-1");
	    map.put("p.guessTotal", "true");
	    
	    // Order by Title
	    map.put("orderby", "@jcr:content/jcr:title");
	    
		return map;
		
	}
	
	/* A method to turn the results of the Query into an ordered map of Groups and their Users */
	private TreeMap<String, List<Map<String,String>>> createGroupList() {
		
		TreeMap<String, List<Map<String,String>>> membersByGroup = new TreeMap<String, List<Map<String,String>>>();
		Iterator<Hit> hits = hitList.iterator();
		String source = null;
		
		if (isCenternet) {
			source = "centernet";
		} else if (isWww) {
			source = "public";
		} else if (isLabs) {
			source = "research";
		}
		
		while (hits.hasNext()) {
			
			try {
				Group g = hits.next().getResource().adaptTo(Group.class);
				String groupName = new String();
				
				ArrayList<Map<String,String>> itemList = new ArrayList<Map<String,String>>();
				
				if (source == null || !g.getPath().contains(source)) {
					continue;
				}
				
				Iterator<Authorizable> members = g.getMembers();
				if (g.hasProperty(PN_USER_NAME)) {
					groupName = g.getProperty(PN_USER_NAME)[0].getString();
				} else {
					groupName = "No group name";
				}
								
				while (members.hasNext()) {
					
					Map<String,String> map = new HashMap<String,String>();
					Authorizable m = members.next();
					if (m instanceof User) {

						map.put("name", m.getID());
						map.put("path", m.getPath());
						
					}

					if (!itemList.contains(map)) {
						itemList.add(map);
					}
					
				}
				
				membersByGroup.put(groupName, itemList);

			} catch (RepositoryException e) {
				log.error("USER LISTER: problem adapting hit to Group: " + e.getMessage());
			}
			
		}
		
		return membersByGroup;
		
	}
	
	/* A method to output a help message to the user if there are no selectors in the URL */
	private void displayHelpMessage(PrintWriter writer) {
		
		writer.print("<p>The User Lister requires the use of Sling Selectors in"
				+ " order to work. Please add one of the following to the URL of"
				+ " this page, just before the '.html'</p>");
		writer.print("<ul>");
		writer.print("<li><b>.centernet</b> to display all CenterNet Users and Groups</li>");
		writer.print("<li><b>.www</b> to display all public site Users and Groups</li>");
		writer.print("<li><b>.labs</b> to display all research site Users and Groups</li>");
		writer.print("</ul>");
		
	}
	
}
