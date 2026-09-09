update jsimplelist.profiles
set avatar_colour = 'grey'
where avatar_colour = 'teal';

alter table jsimplelist.profiles
    drop constraint profiles_avatar_colour_check;

alter table jsimplelist.profiles
    add constraint profiles_avatar_colour_check
    check (
        avatar_colour in (
            'blue',
            'purple',
            'pink',
            'orange',
            'green',
            'grey',
            'red',
            'brown'
        )
    );
