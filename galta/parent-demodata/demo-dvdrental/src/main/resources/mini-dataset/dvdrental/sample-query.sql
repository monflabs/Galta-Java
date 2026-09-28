-- Some requests to create these data sets
--

select * from film where film_id in (1,2,3,4,5,6,7,8,9) order by film_id

select * from language order by language_id

select * from film_category
        where film_id in (1,2,3,4,5,6,7,8,9) 
        order by category_id
        
select * from category
        order by category_id

select * from actor
        order by actor_id

select * from film_actor
        where film_id in (1,2,3,4,5,6,7,8,9) 
        order by actor_id

select * from film_category L 
        left outer join category C ON L.category_id=C.category_id
        where L.film_id in (1,2,3,4,5,6,7,8,9) 
        order by film_id

        
select film_id, last_name, first_name from film_actor A 
        left outer join actor C ON A.actor_id=C.actor_id
        where A.film_id in (1,2,3,4,5,6,7,8,9) 
        order by A.film_id, C.last_name, C.first_name
