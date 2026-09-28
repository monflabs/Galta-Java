let isLoading = true;
// Simulates an async operation instead of a real network fetch
new Promise((resolve) => resolve({ users: ['Alice', 'Bob'] }))
    .then(data => console.log('Loaded:', data.users))
    .catch(err => console.error(err))
    .finally(() => {
        isLoading = false;
        console.log('Finished loading!!');
    })