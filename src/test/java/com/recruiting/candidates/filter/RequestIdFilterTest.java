package com.recruiting.candidates.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruiting.candidates.util.RequestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void wellFormedCallerIdIsPropagatedToMdcAndResponseThenCleared() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "upstream-123.abc_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idSeenDownstream = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> idSeenDownstream.set(MDC.get(RequestContext.REQUEST_ID_KEY)));

        assertThat(idSeenDownstream.get()).isEqualTo("upstream-123.abc_DEF");
        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isEqualTo("upstream-123.abc_DEF");
        assertThat(MDC.get(RequestContext.REQUEST_ID_KEY)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "has space",
                "line\nbreak",
                "{json}",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            })
    void missingOrUnsafeIdIsReplacedWithGeneratedUuid(String supplied) {
        String resolved = RequestIdFilter.resolveRequestId(supplied);

        assertThat(resolved).isNotEqualTo(supplied).matches("[0-9a-f-]{36}");
    }
}
