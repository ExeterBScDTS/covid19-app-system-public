# Token API: Upload single test result (once)

API group: [Upload](../ag-architecture-guidebook#System-APIs-and-Interfaces)

This API provides upload endpoints for UK wide integration of virology test result delivery to the NHS CV19 App System. Uploaded tests are distributed to mobile clients with a latency of min 2h, best we expect 4h, worst case 6 to 24 hours.

The endpoint URL has path elements specific to the external system using it, for instance `eng` for test results sent from the english system or `wls` for results sent by the welsh system.

Please note: the Token API and the Test Lab API are conceptually the same endpoint. The key difference is that the Test Lab API expects a ctaToken as input (generated by our system) and the Token API returns a ctaToken. These two APIs will eventually be merged into the Test Lab API (sometime after the national launch).

Please note: You must call either the Token API or the Test Lab API to send a test result to the backend (not both at the same time)

## Endpoints

- System (England) posts a json test result: ```POST https://<FQDN>/upload/virology-test/eng-result-tokengen```
- System (Wales) posts a json test result: ```POST https://<FQDN>/upload/virology-test/wls-result-tokengen```

## Payloads

See also Test Lab API for valid testResult codes.

### System (England): test result upload AND ctaToken generation

```json
POST https://<FQDN>/upload/virology-test/eng-result-tokengen
{
    "testEndDate": "2020-04-23T00:00:00Z",
    "testResult": "NEGATIVE"
}
```

Response body
``` json
{
  "ctaToken": "1234abcd"
}
```

### System (Wales): test result upload AND ctaToken generation

```json
POST https://<FQDN>/upload/virology-test/wls-result-tokengen
{
    "testEndDate": "2020-05-23T00:00:00Z",
    "testResult": "POSITIVE"
}
```

Response body
``` json
{
  "ctaToken": "1234abcd"
}
```

## Validation

- `ctaToken` Token must be valid according to regular expression `[^a-z0-9]` (any combination of small chars and numbers).
- `testEndDate` ISO8601 format in UTC. Example: `2020-04-23T00:00:00Z`. Time is set to `0` to obfuscate test result relation to personal data
- `testResult` one of the following
  - eng `POSITIVE | NEGATIVE | VOID`
  - wls `POSITIVE | NEGATIVE | INDETERMINATE`
- **One-time upload only**: we don't accept multiple uploads with the same ctaToken. Once uploaded with `202` the test result will be destributed to all mobile clients.
- Please note: INDETERMINATE and VOID are both treated as VOID by the mobile application - the behaviour could change in the future

## Response Codes

Default -> [Upload Response Codes](../api-patterns.md#Upload)

## Javascript to validate Token

```javascript
function validateAppRefCode(code) {
  var CROCKFORD_BASE32 = "0123456789abcdefghjkmnpqrstvwxyz";
  var cleaned = code.toLowerCase().replace(/il/g, "1").replace(/o/g, "0").replace(/u/g, "v").replace(/[- ]/g, "");
  var i;
  var checksum = 0;
  var digit;

  for (i = 0; i < cleaned.length; i++) {
    digit = CROCKFORD_BASE32.indexOf(cleaned.charAt(i));
    checksum = damm32(checksum, digit);
  }

  return checksum == 0;
}

function damm32(checksum, digit) {
  var DAMM_MODULUS = 32;
  var DAMM_MASK = 5;

  checksum ^= digit;
  checksum *= 2;
  if (checksum >= DAMM_MODULUS) {
    checksum = (checksum ^ DAMM_MASK) % DAMM_MODULUS;
  }
  return checksum;
}
```
