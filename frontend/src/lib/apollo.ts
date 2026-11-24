import {
  ApolloClient,
  ApolloLink,
  HttpLink,
  InMemoryCache,
  Observable,
} from "@apollo/client";
import { onError } from "@apollo/client/link/error";
import { ensureCsrfToken, getCsrfTokenSync } from "./csrf";
import { getStoredRegion } from "./subject";

const csrfHeaderLink = new ApolloLink((operation, forward) => {
  return new Observable((observer) => {
    let subscription: { unsubscribe: () => void } | null = null;

    (async () => {
      const token = getCsrfTokenSync() ?? (await ensureCsrfToken());
      const region = getStoredRegion();

      operation.setContext(({ headers = {} }: { headers?: Record<string, string> }) => ({
        headers: {
          ...headers,
          ...(token ? { "X-CSRF-Token": token } : {}),
          "X-Consent-Region": region,
        },
      }));

      subscription = forward(operation).subscribe({
        next: (value) => observer.next(value),
        error: (err) => observer.error(err),
        complete: () => observer.complete(),
      });
    })().catch((err) => observer.error(err));

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  });
});

const csrfRetryErrorLink = onError(({ networkError, operation, forward }) => {
  if (
    networkError &&
    "statusCode" in networkError &&
    networkError.statusCode === 403 &&
    !operation.getContext().csrfRetried
  ) {
    return new Observable((observer) => {
      ensureCsrfToken(true)
        .then((freshToken) => {
          operation.setContext(({ headers = {} }: { headers?: Record<string, string> }) => ({
            csrfRetried: true,
            headers: {
              ...headers,
              ...(freshToken ? { "X-CSRF-Token": freshToken } : {}),
            },
          }));
          const subscriber = {
            next: observer.next.bind(observer),
            error: observer.error.bind(observer),
            complete: observer.complete.bind(observer),
          };
          forward(operation).subscribe(subscriber);
        })
        .catch((err) => observer.error(err));
    });
  }
  return undefined;
});

const httpLink = new HttpLink({
  uri: "/graphql",
  credentials: "include",
});

export const apolloClient = new ApolloClient({
  link: ApolloLink.from([csrfRetryErrorLink, csrfHeaderLink, httpLink]),
  cache: new InMemoryCache({
    typePolicies: {
      SduiNode: {
        // SduiNode IDs are unique per screen; disable global normalization collisions across screens
        keyFields: false,
      },
      SduiScreen: {
        keyFields: false,
      },
      ConsentStatusBundle: {
        keyFields: ["subjectKey"],
      },
    },
  }),
  defaultOptions: {
    watchQuery: {
      fetchPolicy: "cache-and-network",
    },
  },
});
