package com.salesmanager.shop.tags;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import java.io.IOException;

public class CommonResponseHeadersTag extends SimpleTagSupport {

    private String characterEncoding;

    private String cacheControl;

    private String pragma;

    private Long expires;



    @Override
    public void doTag() throws JspException, IOException {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        HttpServletResponse response = requestAttributes.getResponse();
        assert response != null;
        if (!StringUtils.isEmpty(characterEncoding)) {
            response.setCharacterEncoding(characterEncoding);
        }
        if (!StringUtils.isEmpty(cacheControl)) {
            response.setHeader("Cache-Control", cacheControl);
        }
        if (!StringUtils.isEmpty(pragma)) {
            response.setHeader("Pragma", cacheControl);
        }
        if (expires != null) {
            response.setDateHeader("Expires", expires);
        }
    }

    public void setCacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
    }

    public void setPragma(String pragma) {
        this.pragma = pragma;
    }

    public void setExpires(Long expires) {
        this.expires = expires;
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }
}
