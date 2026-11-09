package com.ts.service.impl;

import com.ts.common.constants.Constants;
import com.ts.dao.*;
import com.ts.model.entity.GlobalParams;
import com.ts.model.entity.WfParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the D1 fix in {@link ParamsServiceImpl#getWfParamsByIdAsMap}:
 * it now looks the runtime params record up by {@code wfInstanceId} via
 * {@code WfParameterDao.getWfParams} (selectBywfInstanceId), instead of the
 * randomly-generated primary key via {@code getWfParamById} — which previously
 * always missed and fell back to global params during recovery. The global
 * fallback is preserved only when no WF_PARAMS record exists.
 */
@ExtendWith(MockitoExtension.class)
class ParamsServiceImplTest {

    private static final String WF_INSTANCE_ID = "wf-inst-100";

    @Mock
    private GlobalParamsDao globalParamsDao;
    @Mock
    private WfDefinitionDao wfDefinitionDao;
    @Mock
    private TaskDefinitionDao taskDefinitionDao;
    @Mock
    private WfParameterDao wfParameterDao;
    @Mock
    private TaskParameterDao taskParameterDao;
    @Mock
    private WfInstanceDao wfInstanceDao;
    @Mock
    private TaskInstanceDao taskInstanceDao;

    private ParamsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParamsServiceImpl(globalParamsDao, wfDefinitionDao, taskDefinitionDao,
                wfParameterDao, taskParameterDao, wfInstanceDao, taskInstanceDao);
    }

    // ==================== D1: look up by wfInstanceId ====================

    @Test
    void getWfParamsByIdAsMap_found_returnsRuntimeParamsByInstanceId() {
        WfParameter record = new WfParameter();
        record.setWfInstanceId(WF_INSTANCE_ID);
        record.setPvalue("{\"env\":\"prod\",\"task_timeout\":60}");
        // D1 fix: must query by wfInstanceId (selectBywfInstanceId), not by primary key
        when(wfParameterDao.getWfParams(WF_INSTANCE_ID)).thenReturn(record);

        Map<String, Object> result = service.getWfParamsByIdAsMap(WF_INSTANCE_ID);

        assertNotNull(result);
        assertEquals("prod", result.get("env"));
        // Jackson deserialises 60 as Integer
        assertEquals(60, ((Number) result.get("task_timeout")).intValue());
        // Must NOT use the by-primary-key method
        verify(wfParameterDao, never()).getWfParamById(anyString());
    }

    // ==================== global fallback (preserved) ====================

    @Test
    void getWfParamsByIdAsMap_noRecord_returnsGlobalParams() {
        when(wfParameterDao.getWfParams(WF_INSTANCE_ID)).thenReturn(null);
        GlobalParams gp = new GlobalParams();
        gp.setPvalue("{\"env\":\"global-prod\"}");
        when(globalParamsDao.getGlobalParams(Constants.WF_GLOBAL_PARAM_NAME)).thenReturn(gp);

        Map<String, Object> result = service.getWfParamsByIdAsMap(WF_INSTANCE_ID);

        assertNotNull(result);
        assertEquals("global-prod", result.get("env"));
    }

    @Test
    void getWfParamsByIdAsMap_nullPvalue_returnsGlobalParams() {
        WfParameter record = new WfParameter();
        record.setWfInstanceId(WF_INSTANCE_ID);
        record.setPvalue(null);
        when(wfParameterDao.getWfParams(WF_INSTANCE_ID)).thenReturn(record);
        GlobalParams gp = new GlobalParams();
        gp.setPvalue("{\"env\":\"global-prod\"}");
        when(globalParamsDao.getGlobalParams(Constants.WF_GLOBAL_PARAM_NAME)).thenReturn(gp);

        Map<String, Object> result = service.getWfParamsByIdAsMap(WF_INSTANCE_ID);

        assertNotNull(result);
        assertEquals("global-prod", result.get("env"));
    }

    @Test
    void getWfParamsByIdAsMap_noRecordAndNoGlobalParams_returnsEmptyMap() {
        when(wfParameterDao.getWfParams(WF_INSTANCE_ID)).thenReturn(null);
        when(globalParamsDao.getGlobalParams(Constants.WF_GLOBAL_PARAM_NAME)).thenReturn(null);

        Map<String, Object> result = service.getWfParamsByIdAsMap(WF_INSTANCE_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getWfParamsByIdAsMap_invalidJson_returnsEmptyMap() {
        WfParameter record = new WfParameter();
        record.setWfInstanceId(WF_INSTANCE_ID);
        record.setPvalue("not-a-json");
        when(wfParameterDao.getWfParams(WF_INSTANCE_ID)).thenReturn(record);

        Map<String, Object> result = service.getWfParamsByIdAsMap(WF_INSTANCE_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
