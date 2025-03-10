package com.binance.api.client.impl;

import com.binance.api.client.BinanceApiError;
import com.binance.api.client.config.BinanceApiConfig;
import com.binance.api.client.domain.general.WithRateLimits;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.security.AuthenticationInterceptor;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;

/**
 * Generates a Binance API implementation based on @see {@link BinanceApiService}.
 */
public class BinanceApiServiceGenerator {

    private static final OkHttpClient sharedClient;
    private static final Converter.Factory converterFactory = JacksonConverterFactory.create();

    static {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(500);
        dispatcher.setMaxRequests(500);
        sharedClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .pingInterval(20, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.MINUTES)
                .writeTimeout(1, TimeUnit.MINUTES)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static final Converter<ResponseBody, BinanceApiError> errorBodyConverter =
            (Converter<ResponseBody, BinanceApiError>)converterFactory.responseBodyConverter(
                    BinanceApiError.class, new Annotation[0], null);

    public static <S> S createService(Class<S> serviceClass) {
        return createService(serviceClass, null, null);
    }

    /**
     * Create a Binance API service.
     *
     * @param serviceClass the type of service.
     * @param apiKey Binance API key.
     * @param secret Binance secret.
     *
     * @return a new implementation of the API endpoints for the Binance API service.
     */
    public static <S> S createService(Class<S> serviceClass, String apiKey, String secret) {
        String baseUrl = null;
        if (!BinanceApiConfig.useTestnet) { baseUrl = BinanceApiConfig.getApiBaseUrl(); }
        else {
            baseUrl = /*BinanceApiConfig.useTestnetStreaming ?
                BinanceApiConfig.getStreamTestNetBaseUrl() :*/
                BinanceApiConfig.getTestNetBaseUrl();
        }

        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(converterFactory);

        if (StringUtils.isEmpty(apiKey) || StringUtils.isEmpty(secret)) {
            retrofitBuilder.client(sharedClient);
        } else {
            // `adaptedClient` will use its own interceptor, but share thread pool etc with the 'parent' client
            AuthenticationInterceptor interceptor = new AuthenticationInterceptor(apiKey, secret);
            OkHttpClient adaptedClient = sharedClient.newBuilder().addInterceptor(interceptor).build();
            retrofitBuilder.client(adaptedClient);
        }

        Retrofit retrofit = retrofitBuilder.build();
        return retrofit.create(serviceClass);
    }

    /**
     * Execute a REST call and block until the response is received.
     */
    public static <T> T executeSync(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (response.isSuccessful()) {
                extractRateLimits(response);
                return response.body();
            } else {
                BinanceApiError apiError = getBinanceApiError(response);
                throw new BinanceApiException(apiError);
            }
        } catch (IOException e) {
            throw new BinanceApiException(e);
        }
    }

    static final String RATE_LIMIT_HEADER_PREFIX = "x-mbx-";
    protected static <T> void extractRateLimits(Response<T> response) {
        if (!(response.body() instanceof WithRateLimits)) {
            return;
        }
        Map<String, Integer> rateLimits = ((WithRateLimits) response.body()).getRateLimits();
        Headers headers = response.headers();
        for (String name : headers.names()) {
            if (name.startsWith(RATE_LIMIT_HEADER_PREFIX)) {
                String key = name.substring(RATE_LIMIT_HEADER_PREFIX.length());
                if (key.toLowerCase().startsWith("used-weight") || key.toLowerCase().startsWith("order-count")) {
                    rateLimits.put(key, Integer.parseInt(headers.get(name)));
                }
            }
        }
    }
    
    /**
     * Extracts and converts the response error body into an object.
     */
    public static BinanceApiError getBinanceApiError(Response<?> response) throws IOException, BinanceApiException {
        return errorBodyConverter.convert(response.errorBody());
    }

    /**
     * Returns the shared OkHttpClient instance.
     */
    public static OkHttpClient getSharedClient() {
        return sharedClient;
    }
}
