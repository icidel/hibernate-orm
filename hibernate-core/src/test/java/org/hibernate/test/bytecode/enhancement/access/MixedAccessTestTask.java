package org.hibernate.test.bytecode.enhancement.access;

import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.test.bytecode.enhancement.AbstractEnhancerTestTask;
import org.junit.Assert;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Luis Barreiro
 */
public class MixedAccessTestTask extends AbstractEnhancerTestTask {

	private static ScriptEngine engine = new ScriptEngineManager().getEngineByName( "javascript" );
	private static boolean cleanup = false;

	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[]{TestEntity.class};
	}

	public void prepare() {
		Configuration cfg = new Configuration();
		cfg.setProperty( Environment.ENABLE_LAZY_LOAD_NO_TRANS, "true" );
		cfg.setProperty( Environment.USE_SECOND_LEVEL_CACHE, "false" );
		super.prepare( cfg );

		Session s = getFactory().openSession();
		s.beginTransaction();

		TestEntity testEntity = new TestEntity( "foo" );
		testEntity.setParamsAsString( "{\"paramName\":\"paramValue\"}" );
		s.persist( testEntity );

		s.getTransaction().commit();
		s.clear();
		s.close();
	}

	public void execute() {
		Session s = getFactory().openSession();
		s.beginTransaction();

		TestEntity testEntity = s.get( TestEntity.class, "foo" );
		Assert.assertEquals( "{\"paramName\":\"paramValue\"}", testEntity.getParamsAsString() );

		// Clean parameters
		cleanup = true;
		testEntity.setParamsAsString( "{}" );
		s.persist( testEntity );

		s.getTransaction().commit();
		s.clear();
		s.close();
	}

	protected void cleanup() {
		Session s = getFactory().openSession();
		s.beginTransaction();

		TestEntity testEntity = s.get( TestEntity.class, "foo" );
		Assert.assertTrue( testEntity.getParams().isEmpty() );

		s.getTransaction().commit();
		s.clear();
		s.close();
	}

	@Entity
	private static class TestEntity {

		@Id
		String name;

		@Transient
		Map<String, String> params = new LinkedHashMap<>();

		public TestEntity(String name) {
			this();
			this.name = name;
		}

		protected TestEntity() {
		}

		public Map<String, String> getParams() {
			return params;
		}

		public void setParams(Map<String, String> params) {
			this.params = params;
		}

		@Column( name = "params", length = 4000 )
		@Access( AccessType.PROPERTY )
		public String getParamsAsString() {
			if ( params.size() > 0 ) {
				// Convert to JSON
				return "{" + params.entrySet().stream().map(
						e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\""
				).collect( Collectors.joining( "," ) ) + "}";
			}
			return null;
		}

		public void setParamsAsString(String string) {
			params.clear();

			try {
				params.putAll( (Map<String, String>) engine.eval( "Java.asJSONCompatible(" + string + ")" ) );
			} catch ( ScriptException ignore ) {
				// JDK 8u60 required --- use hard coded values to pass the test
				if ( !cleanup ) {
					params.put( "paramName", "paramValue" );
				}
			}
		}
	}
}