/*
 * Description: Factory building a HikariCP DataSource from a Properties map
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.util.Properties;
/**
 * Factory that builds a HikariCP {@link javax.sql.DataSource} from a
 * {@link java.util.Properties} map.
 *
 * <p>Key properties: {@code jdbcUrl}, {@code username}, {@code password},
 * {@code driverClassName}, {@code maximumPoolSize}, {@code minimumIdle}.
 * Falls back to sensible defaults for pool-sizing parameters if not specified.
 */
public class HikariDataSourceFactory implements DataSourceFactory {
    private static final Logger logger = LogManager.getLogger(HikariDataSourceFactory.class);
    private Properties properties;

    @Override
    /**
     * Sets the properties.
     *
     * @param props  props value
     */
    public void setProperties(Properties props) {
        this.properties = props;
    }

    @Override
    /**
     * Returns the data source.
     *
     * @return the requested datasource object, or null if not found
     */
    public DataSource getDataSource() {
        HikariConfig config = new HikariConfig();

        // Set required properties
        config.setDriverClassName(properties.getProperty("driver"));
        config.setJdbcUrl(properties.getProperty("url"));
        config.setUsername(properties.getProperty("username"));
        config.setPassword(properties.getProperty("password"));

        // Set optional properties
        if (properties.containsKey("minimumIdle")) {
            config.setMinimumIdle(Integer.parseInt(properties.getProperty("minimumIdle")));
        }

        if (properties.containsKey("maximumPoolSize")) {
            config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("maximumPoolSize")));
        }

        if (properties.containsKey("idleTimeout")) {
            config.setIdleTimeout(Long.parseLong(properties.getProperty("idleTimeout")));
        }

        if (properties.containsKey("maxLifetime")) {
            config.setMaxLifetime(Long.parseLong(properties.getProperty("maxLifetime")));
        }

        if (properties.containsKey("connectionTimeout")) {
            config.setConnectionTimeout(Long.parseLong(properties.getProperty("connectionTimeout")));
        }

        if (properties.containsKey("poolName")) {
            config.setPoolName(properties.getProperty("poolName"));
        }

        // Set additional properties (if any)
        for (Object key : properties.keySet()) {
            String propertyName = (String) key;
            if (propertyName.startsWith("dataSource.")) {
                String value = properties.getProperty(propertyName);
                config.addDataSourceProperty(propertyName.substring("dataSource.".length()), value);
            }
        }

        logger.info("Creating HikariDataSource with driver: {}, url: {}",
                properties.getProperty("driver"), properties.getProperty("url"));

        return new HikariDataSource(config);
    }
}