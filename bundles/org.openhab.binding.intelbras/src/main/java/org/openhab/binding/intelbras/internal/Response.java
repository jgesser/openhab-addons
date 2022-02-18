/**
 * Gesser Tecnologia LTDA - ME.
 * 16 de fev de 2022
 */
package org.openhab.binding.intelbras.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.api.ContentResponse;

/**
 * @author Julio Gesser
 */
@NonNullByDefault
public class Response {

    private ContentResponse contentResponse;

    public Response(ContentResponse contentResponse) {
        this.contentResponse = contentResponse;
    }

    public String getMediaType() {
        return contentResponse.getMediaType();
    }

    public String getEncoding() {
        return contentResponse.getEncoding();
    }

    public byte[] getContent() {
        return contentResponse.getContent();
    }

    public String getContentAsString() {
        return contentResponse.getContentAsString();
    }

    @Override
    public String toString() {
        return contentResponse.toString();
    }

    public String getContentAsBase64() {
        return "data:" + contentResponse.getMediaType() + ";base64,"
                + new String(Base64.getEncoder().encode(contentResponse.getContent()), StandardCharsets.UTF_8);
    }
}
