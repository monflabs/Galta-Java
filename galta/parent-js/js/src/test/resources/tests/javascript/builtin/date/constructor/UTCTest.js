assertEquals( Date.parse('2020 Jan 1 GMT'), Date.UTC(2020) )
assertEquals( Date.parse('2020 Feb 1 GMT'), Date.UTC(2020,1) )
assertEquals( Date.parse('2020 Feb 14 GMT'), Date.UTC(2020,1,14) )

assertEquals( Date.parse('2020 Feb 14 03:00:00 PM GMT'), Date.UTC(2020,1,14,15) )
assertEquals( Date.parse('2020 Feb 14 03:16:00 PM GMT'), Date.UTC(2020,1,14,15,16) )
assertEquals( Date.parse('2020 Feb 14 03:16:44 PM GMT'), Date.UTC(2020,1,14,15,16,44) )

assertEquals( Date.parse('2020 Feb 14 03:16:44.656 PM GMT'), Date.UTC(2020,1,14,15,16,44,656) )
