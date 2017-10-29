package de.westnordost.streetcomplete.view;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.List;

public abstract class ListAdapter<T> extends RecyclerView.Adapter<ListAdapter.ViewHolder<T>>
{
	private List<T> list;

	public ListAdapter(List<T> list)
	{
		this.list = list;
	}

	@Override public void onBindViewHolder(ListAdapter.ViewHolder<T> holder, int position)
	{
		holder.onBind(list.get(position));
	}

	@Override public int getItemCount()
	{
		return list.size();
	}

	protected List<T> getList()
	{
		return list;
	}

	public static abstract class ViewHolder<U> extends RecyclerView.ViewHolder
	{
		public ViewHolder(View itemView)
		{
			super(itemView);
			onCreate();
		}

		protected void onCreate() {}
		protected abstract void onBind(U with);
	}
}
