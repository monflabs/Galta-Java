function processData(arrayData) {
    return arrayData.map(data => {
        try {
          const json = JSON.parse(data);
          return json;
        } catch (err) {
          throw new Error(
            `Data processing failed`,
            {cause: err}
          );
        }
      });
  }

  try {
    console.log(processData(['{"one":1,"two":2}', 'not-json']));
  } catch (err) {
    console.log(err.message);
    console.log(err.cause.message);
  }